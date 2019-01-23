package dk.innovation.service;

import io.swagger.annotations.ApiOperation;
import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.core.Response;
import org.slf4j.Logger;

@Path("/v1")
public class ProductImportRestService {

  @Inject
  private Logger logger;

  @GET
  @Path("/import/product")
  //@RolesAllowed(Roles.ADMIN)
  @ApiOperation(value = "hidden dk.innovation.service", hidden = true)
  public Response importProduct() {
    logger.info("hit endpoint for /import/product");
    return Response.ok().build();
  }

  @GET
  @Path("export/product")
  //@RolesAllowed(Roles.ADMIN)
  @ApiOperation(value = "hidden dk.innovation.service", hidden = true)
  public Response importTryggOrders() {
    return Response.ok().build();
  }
}