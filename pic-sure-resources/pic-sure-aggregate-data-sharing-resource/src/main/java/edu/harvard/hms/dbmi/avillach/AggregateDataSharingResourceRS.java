package edu.harvard.hms.dbmi.avillach;

import java.io.IOException;
import java.util.*;

import javax.inject.Inject;
import javax.ws.rs.*;
import javax.annotation.Resource;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.Response;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;

import edu.harvard.dbmi.avillach.domain.*;
import edu.harvard.dbmi.avillach.service.IResourceRS;
import edu.harvard.dbmi.avillach.util.exception.ApplicationException;
import edu.harvard.dbmi.avillach.util.exception.PicsureQueryException;
import edu.harvard.dbmi.avillach.util.exception.ProtocolException;

import org.apache.http.Header;
import org.apache.http.message.BasicHeader;
import org.apache.http.HttpResponse;
import org.apache.http.HttpEntity;
import org.apache.http.util.EntityUtils;
import org.apache.http.client.entity.EntityBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static edu.harvard.dbmi.avillach.util.HttpClientUtil.*;

@Path("/aggregate-data-sharing")
@Produces("application/json")
@Consumes("application/json")
public class AggregateDataSharingResourceRS implements IResourceRS {

    @Inject
    private ApplicationProperties properties;

    private static final String BEARER_STRING = "Bearer ";

    private final static ObjectMapper json = new ObjectMapper();
    private Logger logger = LoggerFactory.getLogger(this.getClass());

    public enum ResultType {
        COUNT,
        CROSS_COUNT,
        INFO_COLUMN_LISTING
    }

    public AggregateDataSharingResourceRS() {}

    @Inject
    public AggregateDataSharingResourceRS(ApplicationProperties applicationProperties) {
        this.properties = applicationProperties;
    }

    private Header[] headers = {
        new BasicHeader(HttpHeaders.AUTHORIZATION, BEARER_STRING + properties.getTargetPicsureToken())
    };

    @GET
    @Path("/status")
    public Response status() {
        logger.debug("Calling Aggregate Data Sharing Resource status()");
        return Response.ok().build();
    }

    @POST
    @Path("/info")
    @Override
    public ResourceInfo info(QueryRequest queryRequest) {
        logger.debug("Calling Aggregate Data Sharing Resource info()");
        return new ResourceInfo();
    }

    @POST
    @Path("/search")
    @Override
    public SearchResults search(QueryRequest searchJson) {
        logger.debug("Calling Aggregate Data Sharing Resource search()");
        return new SearchResults();
    }

    @POST
    @Path("/query")
    @Override
    public QueryStatus query(QueryRequest queryJson) {
        logger.debug("Calling Aggregate Data Sharing Resource query()");
        throw new UnsupportedOperationException("Query is not implemented in this resource.  Please use query/sync");
    }

    @POST
    @Path("/query/{resourceQueryId}/status")
    @Override
    public QueryStatus queryStatus(@PathParam("resourceQueryId") String queryId, QueryRequest statusQuery) {
        logger.debug("Calling Aggregate Data Sharing Resource queryStatus() for query {}", queryId);
        throw new UnsupportedOperationException("Query status is not implemented in this resource.  Please use query/sync");
    }

    @POST
    @Path("/query/{resourceQueryId}/result")
    @Override
    public Response queryResult(@PathParam("resourceQueryId") String queryId, QueryRequest resultRequest) {
        logger.debug("Calling Aggregate Data Sharing Resource queryResult() for query {}", queryId);
        throw new UnsupportedOperationException("Query result is not implemented in this resource.  Please use query/sync");
    }

    @POST
    @Path("/query/sync")
    @Override
    public Response querySync(QueryRequest queryRequest) {
        logger.debug("Calling Aggregate Data Sharing Resource querySync()");
        if (queryRequest == null || queryRequest.getQuery() == null) {
            throw new ProtocolException(ProtocolException.MISSING_DATA);
        }

        try {
            Object query = queryRequest.getQuery();
            UUID resourceUUID = queryRequest.getResourceUUID();

            JsonNode jsonNode = json.valueToTree(query);
            if (!jsonNode.has("expectedResultType")) {
                throw new ProtocolException(ProtocolException.MISSING_DATA);
            }
            String expectedResultType = jsonNode.get("expectedResultType").asText();
            if (!Arrays.asList(ResultType.values()).contains(expectedResultType)) {
                return Response.status(Response.Status.BAD_REQUEST).build();
            }

            String targetPicsureUrl = properties.getTargetPicsureUrl();
            String targetPicsureObfuscationThreshold = properties.getTargetPicsureObfuscationThreshold();

            String queryString = json.writeValueAsString(queryRequest);
            String pathName = "/query/sync";
            String composedURL = composeURL(targetPicsureUrl, pathName);
            logger.debug("Aggregate Data Sharing Resource, sending query: " + queryString + ", to: " + composedURL);

            HttpResponse response = retrievePostResponse(composedURL, headers, queryString);
            if (response.getStatusLine().getStatusCode() != 200) {
                logger.error(composedURL + " calling resource with id " + resourceUUID + " did not return a 200: {} {} ", response.getStatusLine().getStatusCode(), response.getStatusLine().getReasonPhrase());
                throwResponseError(response, targetPicsureUrl);
            }

            int threshold = Integer.parseInt(targetPicsureObfuscationThreshold);
            HttpEntity entity = response.getEntity();
            String entityString = EntityUtils.toString(entity, "UTF-8");
            int queryResult = Integer.parseInt(entityString);
            if (queryResult < threshold) {
                String obfuscation = "< " + targetPicsureObfuscationThreshold;
                entity = EntityBuilder.create().setText(obfuscation).build();
                response.setEntity(entity);
            }

            return Response.ok(response.getEntity().getContent()).build();
        } catch (IOException e) {
            logger.error(e.getMessage());
            throw new ApplicationException("Error encoding query for resource with id " + queryRequest.getResourceUUID());
        } catch (ClassCastException | IllegalArgumentException e) {
            logger.error(e.getMessage());
            throw new ProtocolException(ProtocolException.INCORRECTLY_FORMATTED_REQUEST);
        }
    }
}
