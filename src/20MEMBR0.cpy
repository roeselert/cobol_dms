*> 20MEMBR0.cpy — membership record (VSAM KSDS MEMBER).
*> Primary key :PFX:-ID; alternate keys :PFX:-USER (dups), :PFX:-ORG
*> (dups) and the group :PFX:-USERORG (unique — mirrors the SQL
*> UNIQUE(user_id, org_unit_id) constraint). Roles: ADMIN|EDITOR|VIEWER.
01 :PFX:-REC.
   05 :PFX:-ID          PIC X(36).
   05 :PFX:-USERORG.
      10 :PFX:-USER     PIC X(36).
      10 :PFX:-ORG      PIC X(36).
   05 :PFX:-ROLE        PIC X(10).
