*> 60ORDTR0.cpy — Ordnungsbegriff type catalog (VSAM KSDS ORDNTYPE).
*> Primary key :PFX:-ID; alternate key :PFX:-NAME (unique). :PFX:-ACTIVE
*> "Y"/"N" — only active types are offered to the model. Mirrors
*> ordnungsbegriff_type.
01 :PFX:-REC.
   05 :PFX:-ID          PIC X(36).
   05 :PFX:-NAME        PIC X(100).
   05 :PFX:-DESC        PIC X(300).
   05 :PFX:-ACTIVE      PIC X.
   05 :PFX:-CREATED     PIC 9(13).
