package de.dms;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.dms.conversion.control.JobDispatcher;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end system test through the REST boundary: upload → async
 * conversion → metadata confirmation → Aktenbildung → search (ACL push-down)
 * → RSS feed, plus the 401/403/404/409/415 denial paths of §11.1.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class IngestFlowTest {

    private static final String ADMIN = "admin@example.com";
    private static final String ALICE = "alice@example.com";   // EDITOR in Personal
    private static final String CAROL = "carol@example.com";   // VIEWER in Personal
    private static final String BOB = "bob@example.com";       // no memberships
    private static final byte[] PDF = "%PDF-1.4\n1 0 obj\n<<>>\nendobj\ntrailer\n<<>>\n%%EOF".getBytes();

    private static Path dataDir;
    private static FakeServices fakeServices;
    private static String verwaltungId;
    private static String personalId;
    private static String documentId;
    private static String feedToken;

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JobDispatcher jobDispatcher;

    private final ObjectMapper json = new ObjectMapper();

    @BeforeAll
    static void createDataDir() throws Exception {
        dataDir = Files.createTempDirectory("dms-test");
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) throws Exception {
        Path dir = Files.createTempDirectory("dms-test-db");
        registry.add("dms.data-dir", dir::toString);
        registry.add("spring.datasource.url",
                () -> "jdbc:sqlite:" + dir.resolve("dms.db") + "?journal_mode=WAL&busy_timeout=5000");
        registry.add("dms.worker.enabled", () -> "false");
        registry.add("dms.backup.enabled", () -> "false");
        registry.add("dms.security.mode", () -> "dev");
        registry.add("dms.security.bootstrap-admin-emails", () -> ADMIN);
        // the two document services are faked in-process (hermetic build), but
        // the real HTTP clients run: multipart encoding, bearer auth, mapping
        fakeServices = FakeServices.start();
        registry.add("dms.services.token", () -> FakeServices.TOKEN);
        registry.add("dms.services.conversion.url", () -> fakeServices.baseUrl());
        registry.add("dms.services.extraction.url", () -> fakeServices.baseUrl());
    }

    @AfterAll
    static void stopFakeServices() {
        if (fakeServices != null) {
            fakeServices.stop();
        }
    }

    @Test
    @Order(1)
    void unauthenticatedRequestsAreRejectedWith401() throws Exception {
        mvc.perform(get("/api/v1/documents")).andExpect(status().isUnauthorized());
        mvc.perform(multipart("/api/v1/documents")
                        .file(new MockMultipartFile("file", "a.pdf", "application/pdf", PDF))
                        .param("orgUnitId", "x"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @Order(2)
    void adminBuildsOrgHierarchyAndAssignsMembers() throws Exception {
        verwaltungId = json.readTree(mvc.perform(post("/api/v1/orgs")
                        .header("X-Dev-User", ADMIN)
                        .contentType("application/json")
                        .content("{\"name\":\"Verwaltung\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString()).get("id").asText();

        personalId = json.readTree(mvc.perform(post("/api/v1/orgs")
                        .header("X-Dev-User", ADMIN)
                        .contentType("application/json")
                        .content("{\"name\":\"Personal\",\"parentId\":\"" + verwaltungId + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString()).get("id").asText();

        // cycle prevention: Verwaltung below its own child Personal -> 409
        mvc.perform(put("/api/v1/orgs/" + verwaltungId)
                        .header("X-Dev-User", ADMIN)
                        .contentType("application/json")
                        .content("{\"parentId\":\"" + personalId + "\"}"))
                .andExpect(status().isConflict());

        mvc.perform(post("/api/v1/orgs/" + personalId + "/members")
                        .header("X-Dev-User", ADMIN)
                        .contentType("application/json")
                        .content("{\"email\":\"" + ALICE + "\",\"role\":\"EDITOR\"}"))
                .andExpect(status().isCreated());

        // duplicate assignment -> 409
        mvc.perform(post("/api/v1/orgs/" + personalId + "/members")
                        .header("X-Dev-User", ADMIN)
                        .contentType("application/json")
                        .content("{\"email\":\"" + ALICE + "\",\"role\":\"EDITOR\"}"))
                .andExpect(status().isConflict());

        mvc.perform(post("/api/v1/orgs/" + personalId + "/members")
                        .header("X-Dev-User", ADMIN)
                        .contentType("application/json")
                        .content("{\"email\":\"" + CAROL + "\",\"role\":\"VIEWER\"}"))
                .andExpect(status().isCreated());
    }

    @Test
    @Order(3)
    void uploadIsValidatedAndReturnsReceived() throws Exception {
        mvc.perform(multipart("/api/v1/documents")
                        .file(new MockMultipartFile("file", "evil.exe", "application/octet-stream",
                                new byte[]{1, 2, 3}))
                        .param("orgUnitId", personalId)
                        .header("X-Dev-User", ALICE))
                .andExpect(status().isUnsupportedMediaType());

        MvcResult result = mvc.perform(multipart("/api/v1/documents")
                        .file(new MockMultipartFile("file", "rechnung.pdf", "application/pdf", PDF))
                        .param("orgUnitId", personalId)
                        .header("X-Dev-User", ALICE))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode doc = json.readTree(result.getResponse().getContentAsString());
        documentId = doc.get("id").asText();
        assertThat(doc.get("status").asText()).isEqualTo("RECEIVED");
    }

    @Test
    @Order(4)
    void workerConvertsDocumentToReadyWithPdfaRendition() throws Exception {
        jobDispatcher.poll();
        JsonNode doc = json.readTree(mvc.perform(get("/api/v1/documents/" + documentId)
                        .header("X-Dev-User", ALICE))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        assertThat(doc.get("status").asText()).isEqualTo("READY");
        assertThat(doc.get("renditions").toString())
                .contains("PDF_A").contains("ORIGINAL").contains("TEXT");
    }

    @Test
    @Order(5)
    void metadataConfirmationFormsAkteIdempotently() throws Exception {
        // missing Ordnungsbegriff -> 422, nothing saved
        mvc.perform(put("/api/v1/documents/" + documentId + "/metadata")
                        .header("X-Dev-User", ALICE)
                        .contentType("application/json")
                        .content("{\"documentDate\":\"2026-07-01\",\"documentClass\":\"RECHNUNG\"}"))
                .andExpect(status().isUnprocessableEntity());

        // value outside controlled vocabulary -> 422
        mvc.perform(put("/api/v1/documents/" + documentId + "/metadata")
                        .header("X-Dev-User", ALICE)
                        .contentType("application/json")
                        .content("{\"documentDate\":\"2026-07-01\",\"documentClass\":\"QUITTUNG\","
                                + "\"filePlanReference\":\"2026/PER/001\"}"))
                .andExpect(status().isUnprocessableEntity());

        mvc.perform(put("/api/v1/documents/" + documentId + "/metadata")
                        .header("X-Dev-User", ALICE)
                        .contentType("application/json")
                        .content("{\"documentDate\":\"2026-07-01\",\"documentClass\":\"RECHNUNG\","
                                + "\"filePlanReference\":\"2026/PER/001\"}"))
                .andExpect(status().isOk());

        // second document with the same Ordnungsbegriff joins the same Akte
        MvcResult second = mvc.perform(multipart("/api/v1/documents")
                        .file(new MockMultipartFile("file", "anlage.pdf", "application/pdf", PDF))
                        .param("orgUnitId", personalId)
                        .header("X-Dev-User", ALICE))
                .andExpect(status().isCreated())
                .andReturn();
        String secondId = json.readTree(second.getResponse().getContentAsString()).get("id").asText();
        jobDispatcher.poll();
        mvc.perform(put("/api/v1/documents/" + secondId + "/metadata")
                        .header("X-Dev-User", ALICE)
                        .contentType("application/json")
                        .content("{\"documentDate\":\"2026-07-02\",\"documentClass\":\"RECHNUNG\","
                                + "\"filePlanReference\":\"2026/PER/001\"}"))
                .andExpect(status().isOk());

        JsonNode akten = json.readTree(mvc.perform(get("/api/v1/akten").header("X-Dev-User", ALICE))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        assertThat(akten).hasSize(1);
        String akteId = akten.get(0).get("id").asText();

        JsonNode akteDocs = json.readTree(mvc.perform(get("/api/v1/akten/" + akteId + "/documents")
                        .header("X-Dev-User", ALICE))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        assertThat(akteDocs).hasSize(2);

        // inheritance: admin sees the akte via the parent unit; strangers get 404 (403≡404)
        mvc.perform(get("/api/v1/akten/" + akteId).header("X-Dev-User", ADMIN))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/akten/" + akteId).header("X-Dev-User", BOB))
                .andExpect(status().isNotFound());
    }

    @Test
    @Order(6)
    void searchEnforcesAclPushDown() throws Exception {
        // no query and no filter -> 400
        mvc.perform(get("/api/v1/search").header("X-Dev-User", ALICE))
                .andExpect(status().isBadRequest());

        JsonNode hits = json.readTree(mvc.perform(get("/api/v1/search")
                        .param("q", "rechnung")
                        .header("X-Dev-User", ALICE))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        assertThat(hits.size()).isGreaterThanOrEqualTo(1);

        // a user without membership never receives a hit (never produced, no leak)
        JsonNode bobHits = json.readTree(mvc.perform(get("/api/v1/search")
                        .param("q", "rechnung")
                        .header("X-Dev-User", BOB))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        assertThat(bobHits).isEmpty();

        // filter search without full-text query
        JsonNode filtered = json.readTree(mvc.perform(get("/api/v1/search")
                        .param("documentClass", "RECHNUNG")
                        .header("X-Dev-User", ALICE))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        assertThat(filtered.size()).isEqualTo(2);
    }

    @Test
    @Order(7)
    void deniedAccessIsUniform404AndInsufficientRoleIs403() throws Exception {
        // foreign unit: 404, indistinguishable from a nonexistent document
        mvc.perform(get("/api/v1/documents/" + documentId).header("X-Dev-User", BOB))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/documents/does-not-exist").header("X-Dev-User", BOB))
                .andExpect(status().isNotFound());

        // VIEWER on a visible document attempting a write: 403
        mvc.perform(put("/api/v1/documents/" + documentId + "/metadata")
                        .header("X-Dev-User", CAROL)
                        .contentType("application/json")
                        .content("{\"documentDate\":\"2026-07-01\",\"documentClass\":\"RECHNUNG\","
                                + "\"filePlanReference\":\"2026/PER/002\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @Order(8)
    void rssFeedIsTokenScopedAndAclFiltered() throws Exception {
        String body = mvc.perform(post("/api/v1/feeds/token").header("X-Dev-User", ALICE))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        feedToken = json.readTree(body).get("token").asText();

        String rss = mvc.perform(get("/api/v1/feeds/inbox.rss").param("token", feedToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(rss).contains("<rss version=\"2.0\">").contains("rechnung.pdf");

        mvc.perform(get("/api/v1/feeds/inbox.rss").param("token", "invalid"))
                .andExpect(status().isUnauthorized());

        // revoked token no longer works
        String tokenId = json.readTree(body).get("id").asText();
        mvc.perform(delete("/api/v1/feeds/token/" + tokenId).header("X-Dev-User", ALICE))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/feeds/inbox.rss").param("token", feedToken))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @Order(9)
    void deleteOfNonEmptyOrgUnitIsRejected() throws Exception {
        mvc.perform(delete("/api/v1/orgs/" + personalId).header("X-Dev-User", ADMIN))
                .andExpect(status().isConflict());
    }

    @Test
    @Order(10)
    void membershipRemovalRevokesAccess() throws Exception {
        JsonNode members = json.readTree(mvc.perform(get("/api/v1/orgs/" + personalId + "/members")
                        .header("X-Dev-User", ADMIN))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        String carolMembershipId = null;
        for (JsonNode member : members) {
            if (CAROL.equals(member.get("email").asText())) {
                carolMembershipId = member.get("membershipId").asText();
            }
        }
        assertThat(carolMembershipId).isNotNull();

        // EDITOR is not enough to manage members -> 403; a stranger gets 404
        mvc.perform(delete("/api/v1/orgs/" + personalId + "/members/" + carolMembershipId)
                        .header("X-Dev-User", ALICE))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/v1/orgs/" + personalId + "/members/" + carolMembershipId)
                        .header("X-Dev-User", BOB))
                .andExpect(status().isNotFound());

        // membership id under the wrong org unit is uniformly 404
        mvc.perform(delete("/api/v1/orgs/" + verwaltungId + "/members/" + carolMembershipId)
                        .header("X-Dev-User", ADMIN))
                .andExpect(status().isNotFound());

        // before removal Carol (VIEWER) can read the document
        mvc.perform(get("/api/v1/documents/" + documentId).header("X-Dev-User", CAROL))
                .andExpect(status().isOk());

        mvc.perform(delete("/api/v1/orgs/" + personalId + "/members/" + carolMembershipId)
                        .header("X-Dev-User", ADMIN))
                .andExpect(status().isNoContent());

        // access is gone (403≡404), and a second removal is 404
        mvc.perform(get("/api/v1/documents/" + documentId).header("X-Dev-User", CAROL))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/api/v1/orgs/" + personalId + "/members/" + carolMembershipId)
                        .header("X-Dev-User", ADMIN))
                .andExpect(status().isNotFound());
    }

    @Test
    @Order(11)
    void documentClassesAndIntentsAreDbBackedAndBootstrapAdminManaged() throws Exception {
        // any authenticated user may read the catalogs; only bootstrap admins may mutate
        mvc.perform(get("/api/v1/document-classes").header("X-Dev-User", ALICE))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/document-classes")
                        .header("X-Dev-User", ALICE)
                        .contentType("application/json")
                        .content("{\"name\":\"SPESEN\",\"description\":\"Reisekostenabrechnungen\"}"))
                .andExpect(status().isForbidden());

        // seed data from the migration is served
        JsonNode config = json.readTree(mvc.perform(get("/api/v1/config").header("X-Dev-User", ALICE))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        assertThat(config.get("documentClasses").toString()).contains("RECHNUNG");

        // blank description -> 422
        mvc.perform(post("/api/v1/document-classes")
                        .header("X-Dev-User", ADMIN)
                        .contentType("application/json")
                        .content("{\"name\":\"SPESEN\",\"description\":\"\"}"))
                .andExpect(status().isUnprocessableEntity());

        String classId = json.readTree(mvc.perform(post("/api/v1/document-classes")
                        .header("X-Dev-User", ADMIN)
                        .contentType("application/json")
                        .content("{\"name\":\"spesen\",\"description\":\"Reisekostenabrechnungen\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString()).get("id").asText();

        // name is normalized to upper case and now serves via /config
        JsonNode updatedConfig = json.readTree(mvc.perform(get("/api/v1/config").header("X-Dev-User", ALICE))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        assertThat(updatedConfig.get("documentClasses").toString()).contains("SPESEN");

        // the new class is now accepted by metadata validation
        mvc.perform(put("/api/v1/documents/" + documentId + "/metadata")
                        .header("X-Dev-User", ALICE)
                        .contentType("application/json")
                        .content("{\"documentDate\":\"2026-07-01\",\"documentClass\":\"SPESEN\","
                                + "\"filePlanReference\":\"2026/PER/001\"}"))
                .andExpect(status().isOk());

        // intents: create with fields, reserved field name rejected, replace fields, delete
        mvc.perform(post("/api/v1/intents")
                        .header("X-Dev-User", ADMIN)
                        .contentType("application/json")
                        .content("{\"name\":\"Test\",\"description\":\"d\","
                                + "\"fields\":[{\"name\":\"documentClass\",\"description\":\"x\"}]}"))
                .andExpect(status().isUnprocessableEntity());

        String intentId = json.readTree(mvc.perform(post("/api/v1/intents")
                        .header("X-Dev-User", ADMIN)
                        .contentType("application/json")
                        .content("{\"name\":\"Bestellung\",\"description\":\"Eine Bestellung\","
                                + "\"fields\":[{\"name\":\"bestellnummer\",\"description\":\"Die Nummer\"}]}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString()).get("id").asText();

        JsonNode intents = json.readTree(mvc.perform(get("/api/v1/intents").header("X-Dev-User", ALICE))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        assertThat(intents.toString()).contains("Bestellung").contains("bestellnummer");

        // PUT replaces the full field list
        JsonNode replaced = json.readTree(mvc.perform(put("/api/v1/intents/" + intentId)
                        .header("X-Dev-User", ADMIN)
                        .contentType("application/json")
                        .content("{\"name\":\"Bestellung\",\"description\":\"Eine Bestellung\","
                                + "\"fields\":[{\"name\":\"lieferdatum\",\"description\":\"Wann geliefert\"}]}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        assertThat(replaced.get("fields").toString())
                .contains("lieferdatum").doesNotContain("bestellnummer");

        mvc.perform(delete("/api/v1/intents/" + intentId).header("X-Dev-User", ADMIN))
                .andExpect(status().isNoContent());
        mvc.perform(delete("/api/v1/document-classes/" + classId).header("X-Dev-User", ADMIN))
                .andExpect(status().isNoContent());

        // a non-bootstrap-admin cannot delete either
        mvc.perform(delete("/api/v1/intents/does-not-exist").header("X-Dev-User", ALICE))
                .andExpect(status().isForbidden());
    }

    @Test
    @Order(12)
    void ordnungsbegriffTypesAreBootstrapAdminManaged() throws Exception {
        // readable by any authenticated user, seed types from the migration served
        JsonNode types = json.readTree(mvc.perform(get("/api/v1/ordnungsbegriff-types")
                        .header("X-Dev-User", ALICE))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        assertThat(types.toString()).contains("Kundennummer").contains("Vertragsnummer");

        // mutations are reserved for bootstrap admins
        mvc.perform(post("/api/v1/ordnungsbegriff-types")
                        .header("X-Dev-User", ALICE)
                        .contentType("application/json")
                        .content("{\"name\":\"Aktenzeichen\",\"description\":\"Das amtliche Aktenzeichen\"}"))
                .andExpect(status().isForbidden());

        // blank name or description -> 422, nothing stored
        mvc.perform(post("/api/v1/ordnungsbegriff-types")
                        .header("X-Dev-User", ADMIN)
                        .contentType("application/json")
                        .content("{\"name\":\"\",\"description\":\"x\"}"))
                .andExpect(status().isUnprocessableEntity());
        mvc.perform(post("/api/v1/ordnungsbegriff-types")
                        .header("X-Dev-User", ADMIN)
                        .contentType("application/json")
                        .content("{\"name\":\"Aktenzeichen\",\"description\":\"\"}"))
                .andExpect(status().isUnprocessableEntity());

        String typeId = json.readTree(mvc.perform(post("/api/v1/ordnungsbegriff-types")
                        .header("X-Dev-User", ADMIN)
                        .contentType("application/json")
                        .content("{\"name\":\"Aktenzeichen\",\"description\":\"Das amtliche Aktenzeichen\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString()).get("id").asText();

        // duplicate name (case-insensitive) -> 409
        mvc.perform(post("/api/v1/ordnungsbegriff-types")
                        .header("X-Dev-User", ADMIN)
                        .contentType("application/json")
                        .content("{\"name\":\"aktenzeichen\",\"description\":\"Dublette\"}"))
                .andExpect(status().isConflict());

        // deactivate via PUT; the type is still served (for re-activation)
        JsonNode updated = json.readTree(mvc.perform(put("/api/v1/ordnungsbegriff-types/" + typeId)
                        .header("X-Dev-User", ADMIN)
                        .contentType("application/json")
                        .content("{\"name\":\"Aktenzeichen\",\"description\":\"Das amtliche Aktenzeichen\","
                                + "\"active\":false}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        assertThat(updated.get("active").asBoolean()).isFalse();

        mvc.perform(delete("/api/v1/ordnungsbegriff-types/" + typeId).header("X-Dev-User", ADMIN))
                .andExpect(status().isNoContent());
        mvc.perform(delete("/api/v1/ordnungsbegriff-types/" + typeId).header("X-Dev-User", ADMIN))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/api/v1/ordnungsbegriff-types/does-not-exist").header("X-Dev-User", ALICE))
                .andExpect(status().isForbidden());
    }

    @Test
    @Order(13)
    void documentWithoutOrdnungsbegriffeIsFlaggedForManualIndexingUntilConfirmed() throws Exception {
        // AI is unconfigured in this suite -> extraction is skipped, so the
        // worker must flag the document for manual indexing (flag-only row).
        String docId = json.readTree(mvc.perform(multipart("/api/v1/documents")
                        .file(new MockMultipartFile("file", "brief.pdf", "application/pdf", PDF))
                        .param("orgUnitId", personalId)
                        .header("X-Dev-User", ALICE))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString()).get("id").asText();
        jobDispatcher.poll();

        JsonNode meta = json.readTree(mvc.perform(get("/api/v1/documents/" + docId + "/metadata")
                        .header("X-Dev-User", ALICE))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        assertThat(meta.get("indexingFlag").asText()).isEqualTo("MANUAL_INDEXING");
        assertThat(meta.get("ordnungsbegriffe")).isEmpty();
        assertThat(meta.get("documentClass").isNull()).isTrue();

        // the user-confirmed save performs the manual indexing and clears the flag
        JsonNode confirmed = json.readTree(mvc.perform(put("/api/v1/documents/" + docId + "/metadata")
                        .header("X-Dev-User", ALICE)
                        .contentType("application/json")
                        .content("{\"documentDate\":\"2026-07-03\",\"documentClass\":\"RECHNUNG\","
                                + "\"filePlanReference\":\"2026/PER/001\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        assertThat(confirmed.get("indexingFlag").isNull()).isTrue();
    }

    @Test
    @Order(14)
    void reprocessRequeuesConversionAndClassification() throws Exception {
        // a stranger cannot even tell the document exists (403≡404)
        mvc.perform(post("/api/v1/documents/" + documentId + "/reprocess").header("X-Dev-User", BOB))
                .andExpect(status().isNotFound());

        // the editor re-runs the pipeline: the document drops back to RECEIVED
        JsonNode requeued = json.readTree(mvc.perform(post("/api/v1/documents/" + documentId + "/reprocess")
                        .header("X-Dev-User", ALICE))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        assertThat(requeued.get("status").asText()).isEqualTo("RECEIVED");

        // and the worker converts it back to READY on a fresh set of attempts
        jobDispatcher.poll();
        JsonNode doc = json.readTree(mvc.perform(get("/api/v1/documents/" + documentId)
                        .header("X-Dev-User", ALICE))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        assertThat(doc.get("status").asText()).isEqualTo("READY");
    }

    @Test
    @Order(15)
    void imageUploadIsConvertedToASearchablePdf() throws Exception {
        // an uploaded image must be turned into a PDF/A by the conversion
        // service — never served as-is. The fake service answers every upload
        // with a valid PDF, so this path is deterministic in the build.
        String docId = json.readTree(mvc.perform(multipart("/api/v1/documents")
                        .file(new MockMultipartFile("file", "scan.png", "image/png", pngBytes()))
                        .param("orgUnitId", personalId)
                        .header("X-Dev-User", ALICE))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString()).get("id").asText();
        jobDispatcher.poll();

        JsonNode doc = json.readTree(mvc.perform(get("/api/v1/documents/" + docId)
                        .header("X-Dev-User", ALICE))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        assertThat(doc.get("status").asText()).isEqualTo("READY");
        assertThat(doc.get("renditions").toString()).contains("PDF_A");

        byte[] pdfa = mvc.perform(get("/api/v1/documents/" + docId + "/file")
                        .param("type", "PDF_A")
                        .header("X-Dev-User", ALICE))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(new String(pdfa, 0, 5, java.nio.charset.StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
    }

    @Test
    @Order(16)
    void aiSuggestionsPrefillMetadataWhenExtractionSucceeds() throws Exception {
        // a scripted extraction answer proves the full applySuggestions path:
        // catalogs travel out, suggestions come back and prefill the metadata
        fakeServices.enqueueExtractResponse("{\"status\":\"ok\",\"suggestions\":{"
                + "\"documentDate\":\"2026-07-05\",\"documentClass\":\"RECHNUNG\","
                + "\"filePlanReference\":null,"
                + "\"additional\":{\"intent\":\"Rechnungseingang\",\"rechnungsnummer\":\"R-2026-001\"},"
                + "\"ordnungsbegriffe\":[{\"type\":\"Kundennummer\",\"value\":\"7000123456\"}]}}");
        String docId = json.readTree(mvc.perform(multipart("/api/v1/documents")
                        .file(new MockMultipartFile("file", "neue-rechnung.pdf", "application/pdf", PDF))
                        .param("orgUnitId", personalId)
                        .header("X-Dev-User", ALICE))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString()).get("id").asText();
        jobDispatcher.poll();

        JsonNode meta = json.readTree(mvc.perform(get("/api/v1/documents/" + docId + "/metadata")
                        .header("X-Dev-User", ALICE))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        assertThat(meta.get("documentClass").asText()).isEqualTo("RECHNUNG");
        assertThat(meta.get("documentDate").asText()).isEqualTo("2026-07-05");
        assertThat(meta.get("ordnungsbegriffe").toString()).contains("7000123456");

        // the detected intent and its extracted fields land in the detail metadata
        assertThat(meta.get("intent").get("name").asText()).isEqualTo("Rechnungseingang");
        assertThat(meta.get("intent").get("fields").get("rechnungsnummer").asText())
                .isEqualTo("R-2026-001");
    }

    @Test
    @Order(17)
    void rssFeedCarriesDetectedIntent() throws Exception {
        // only the order-16 document carries an intent — the default fake
        // /extract answers unconfigured, so no other item may gain a category
        String token = json.readTree(mvc.perform(post("/api/v1/feeds/token").header("X-Dev-User", ALICE))
                        .andExpect(status().isOk())
                        .andReturn().getResponse().getContentAsString())
                .get("token").asText();

        String rss = mvc.perform(get("/api/v1/feeds/inbox.rss").param("token", token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(rss).contains("<category>Rechnungseingang</category>")
                .contains("Intent: Rechnungseingang · rechnungsnummer: R-2026-001");
    }

    @Test
    @Order(18)
    void jobQueueListsVisibleJobsOrderedByStatusAndName() throws Exception {
        mvc.perform(get("/api/v1/jobs")).andExpect(status().isUnauthorized());

        // BOB has no memberships: the queue must not reveal foreign jobs or names
        JsonNode forBob = json.readTree(mvc.perform(get("/api/v1/jobs").header("X-Dev-User", BOB))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        assertThat(forBob).isEmpty();

        JsonNode forAlice = json.readTree(mvc.perform(get("/api/v1/jobs").header("X-Dev-User", ALICE))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        assertThat(forAlice.size()).isGreaterThanOrEqualTo(2);
        String previousName = null;
        for (JsonNode job : forAlice) {
            // everything processed so far is terminal DONE, so names must ascend
            assertThat(job.get("status").asText()).isEqualTo("DONE");
            assertThat(job.get("documentName").asText()).isNotEmpty();
            assertThat(job.get("createdAt").asText()).isNotEmpty();
            String name = job.get("documentName").asText().toLowerCase(java.util.Locale.ROOT);
            if (previousName != null) {
                assertThat(name).isGreaterThanOrEqualTo(previousName);
            }
            previousName = name;
        }
    }

    /** A small but genuine PNG so the real converter has valid input to work on. */
    private static byte[] pngBytes() throws Exception {
        BufferedImage image = new BufferedImage(64, 32, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, 64, 32);
        g.setColor(Color.BLACK);
        g.drawString("DMS", 8, 20);
        g.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }
}
