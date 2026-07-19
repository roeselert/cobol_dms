*> 60INTNR0.cpy — extraction intent catalog (VSAM KSDS EXTRINTENT).
*> Primary key :PFX:-ID; alternate key :PFX:-NAME (unique). Mirrors
*> extraction_intent. The AI system prompt is assembled from these rows.
01 :PFX:-REC.
   05 :PFX:-ID          PIC X(36).
   05 :PFX:-NAME        PIC X(100).
   05 :PFX:-DESC        PIC X(300).
   05 :PFX:-CREATED     PIC 9(13).
