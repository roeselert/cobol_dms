package de.dms.documents.search.boundary;

import de.dms.crosscutting.accesscontrol.control.Authorization;
import de.dms.crosscutting.platform.control.Paging;
import de.dms.documents.search.control.SearchQuery;
import de.dms.crosscutting.security.control.CurrentUser;
import de.dms.crosscutting.security.control.UserRef;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/search")
public class SearchController {

    private final CurrentUser currentUser;
    private final Authorization authorization;
    private final SearchQuery searchQuery;

    public SearchController(CurrentUser currentUser, Authorization authorization, SearchQuery searchQuery) {
        this.currentUser = currentUser;
        this.authorization = authorization;
        this.searchQuery = searchQuery;
    }

    @GetMapping
    public List<SearchQuery.SearchHit> search(@RequestParam(required = false) String q,
                                              @RequestParam(required = false) String documentClass,
                                              @RequestParam(required = false) String filePlanReference,
                                              @RequestParam(required = false) String dateFrom,
                                              @RequestParam(required = false) String dateTo,
                                              @RequestParam(defaultValue = "0") int page,
                                              @RequestParam(defaultValue = "20") int size) {
        UserRef user = currentUser.require();
        // 400 when neither query nor filter is given happens inside SearchQuery
        return searchQuery.search(
                authorization.visibleOrgUnitIds(user),
                q,
                new SearchQuery.Filters(documentClass, filePlanReference, dateFrom, dateTo),
                Paging.page(page), Paging.size(size));
    }
}
