package humble.rest

class TesteRest extends org.scalatra.ScalatraServlet with org.scalatra.scalate.ScalateSupport {

  get("/") {
    <html>
      <body>
        <h1>oi</h1>
      </body>
    </html>
  }

}
