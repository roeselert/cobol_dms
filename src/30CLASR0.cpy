*> 30CLASR0.cpy — document class record (VSAM KSDS DOCCLASS).
*> Primary key :PFX:-ID; alternate key :PFX:-NAME (unique upper-case
*> code, e.g. RECHNUNG). The controlled vocabulary; seeded by 90BOOTW0.
01 :PFX:-REC.
   05 :PFX:-ID          PIC X(36).
   05 :PFX:-NAME        PIC X(50).
   05 :PFX:-DESC        PIC X(200).
   05 :PFX:-CREATED     PIC 9(13).
