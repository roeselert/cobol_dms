*> 30ORDBR0.cpy — document Ordnungsbegriff record (VSAM KSDS
*> DOCORDNB). Primary key :PFX:-ID; alternate key :PFX:-DOC (dups).
*> :PFX:-TYPE is a snapshot of ordnungsbegriff_type.name — deliberately
*> no referential check (catalog edits never touch stored metadata).
01 :PFX:-REC.
   05 :PFX:-ID          PIC X(36).
   05 :PFX:-DOC         PIC X(36).
   05 :PFX:-TYPE        PIC X(100).
   05 :PFX:-VALUE       PIC X(200).
   05 :PFX:-EXTRACTED   PIC X.
   05 :PFX:-CREATED     PIC 9(13).
