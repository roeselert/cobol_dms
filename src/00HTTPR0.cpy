*> 00HTTPR0.cpy — the HTTP exchange record passed between the generic
*> CGI dispatcher (00HTTPC0) and the business boundary programs. This is
*> the ONLY coupling between the HTTP layer and business logic: boundary
*> programs never read CGI environment variables or stdin themselves.
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
   *> Y = the boundary emitted the raw response itself (file download)
   05 HX-EMITTED          PIC X.
   *> query-string parameters, URL-decoded
   05 HX-QP-COUNT         PIC 9(2).
   05 HX-QP OCCURS 8.
      10 HX-QP-NAME       PIC X(32).
      10 HX-QP-VALUE      PIC X(64).
   *> multipart upload staged by 00MPARC0 (file part on disk)
   05 HX-UPLOAD.
      10 HX-UP-PRESENT    PIC X.
      10 HX-UP-PATH       PIC X(512).
      10 HX-UP-FILENAME   PIC X(255).
      10 HX-UP-MIME       PIC X(100).
      10 HX-UP-SIZE       PIC 9(13).
      10 HX-UP-SHA256     PIC X(64).
   *> non-file multipart form fields (e.g. orgUnitId)
   05 HX-FF-COUNT         PIC 9(2).
   05 HX-FF OCCURS 8.
      10 HX-FF-NAME       PIC X(32).
      10 HX-FF-VALUE      PIC X(200).
   05 HX-RESPONSE         PIC X(262144).
