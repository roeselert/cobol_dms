*> 60INFDR0.cpy — extraction intent field (VSAM KSDS INTFIELD).
*> Primary key :PFX:-ID; alternate key :PFX:-INTENT (dups) for the
*> per-intent field scan. :PFX:-NAME is the JSON key the model answers
*> with. Mirrors extraction_intent_field.
01 :PFX:-REC.
   05 :PFX:-ID          PIC X(36).
   05 :PFX:-INTENT      PIC X(36).
   05 :PFX:-NAME        PIC X(100).
   05 :PFX:-DESC        PIC X(300).
