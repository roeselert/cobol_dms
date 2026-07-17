*> 20ORGSR0.cpy — org_unit record (VSAM KSDS ORGUNIT).
*> Primary key :PFX:-ID, alternate keys :PFX:-PATH (unique) and
*> :PFX:-PARENT (duplicates). Mirrors table org_unit (ARCHITECTURE.md §9).
01 :PFX:-REC.
   05 :PFX:-ID          PIC X(36).
   05 :PFX:-NAME        PIC X(200).
   05 :PFX:-PARENT      PIC X(36).
   05 :PFX:-PATH        PIC X(512).
