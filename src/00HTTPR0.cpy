*> 00HTTPR0.cpy — the HTTP exchange record passed between the generic
*> CGI dispatcher (00HTTPC0) and the business boundary programs. This is
*> the ONLY coupling between the HTTP layer and business logic: boundary
*> programs never read CGI environment variables themselves.
01 HTTP-EXCHANGE.
   05 HX-METHOD           PIC X(8).
   05 HX-PATH             PIC X(256).
   *> path split at "/": seg(1)="v1", seg(2)=resource, seg(3)=id, ...
   05 HX-SEG              PIC X(64) OCCURS 6.
   05 HX-BODY             PIC X(4096).
   05 HX-USER-ID          PIC X(36).
   05 HX-USER-EMAIL       PIC X(200).
   *> Y when the caller is a bootstrap admin (DMS_BOOTSTRAP_ADMINS)
   05 HX-USER-ADMIN       PIC X.
   05 HX-STATUS           PIC 9(3).
   05 HX-RESPONSE         PIC X(262144).
