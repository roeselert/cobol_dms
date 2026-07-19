*> 20USRSR0.cpy — dms_user record (VSAM KSDS USERS).
*> Primary key :PFX:-ID, alternate key :PFX:-EMAIL (unique).
*> Status values: INVITED | ACTIVE | DISABLED.
01 :PFX:-REC.
   05 :PFX:-ID          PIC X(36).
   05 :PFX:-EMAIL       PIC X(200).
   05 :PFX:-DNAME       PIC X(200).
   05 :PFX:-STATUS      PIC X(10).
