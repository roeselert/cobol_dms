*> 30RENDR0.cpy — rendition record (VSAM KSDS RENDTION).
*> Primary key :PFX:-ID; alternate keys :PFX:-DOCTYPE (group doc+type,
*> unique — mirrors UNIQUE(document_id, type)) and :PFX:-DOC (dups).
*> Types: ORIGINAL | TEXT (OCR-only conversion, D-9 — no PDF_A).
*> Producers: upload | ocrmypdf.
01 :PFX:-REC.
   05 :PFX:-ID          PIC X(36).
   05 :PFX:-DOCTYPE.
      10 :PFX:-DOC      PIC X(36).
      10 :PFX:-TYPE     PIC X(10).
   05 :PFX:-KEY         PIC X(200).
   05 :PFX:-MIME        PIC X(100).
   05 :PFX:-SIZE        PIC 9(13).
   05 :PFX:-SHA256      PIC X(64).
   05 :PFX:-CREATED     PIC 9(13).
   05 :PFX:-PRODUCER    PIC X(15).
