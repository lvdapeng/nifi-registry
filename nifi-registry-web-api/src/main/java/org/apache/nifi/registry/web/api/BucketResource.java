/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.nifi.registry.web.api;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import org.apache.commons.lang3.StringUtils;
import org.apache.nifi.registry.authorization.Authorizer;
import org.apache.nifi.registry.authorization.RequestAction;
import org.apache.nifi.registry.authorization.resource.Authorizable;
import org.apache.nifi.registry.authorization.user.NiFiUserUtils;
import org.apache.nifi.registry.bucket.Bucket;
import org.apache.nifi.registry.bucket.BucketItem;
import org.apache.nifi.registry.service.AuthorizationService;
import org.apache.nifi.registry.service.RegistryService;
import org.apache.nifi.registry.service.QueryParameters;
import org.apache.nifi.registry.params.SortParameter;
import org.apache.nifi.registry.web.link.LinkService;
import org.apache.nifi.registry.field.Fields;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.ws.rs.BadRequestException;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Component
@Path("/buckets")
@Api(
        value = "/buckets",
        description = "Create named buckets in the registry to store NiFI objects such flows and extensions. " +
                "Search for and retrieve existing buckets."
)
public class BucketResource extends AuthorizableApplicationResource {

    private static final Logger logger = LoggerFactory.getLogger(BucketResource.class);

    @Context
    UriInfo uriInfo;

    private final LinkService linkService;

    private final RegistryService registryService;

    @Autowired
    public BucketResource(
            final RegistryService registryService,
            final LinkService linkService,
            final AuthorizationService authorizationService,
            final Authorizer authorizer) {
        super(authorizer, authorizationService);
        this.registryService = registryService;
        this.linkService = linkService;
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(
            value = "Creates a bucket",
            response = Bucket.class
    )
    @ApiResponses({
            @ApiResponse(code = 400, message = HttpStatusMessages.MESSAGE_400),
            @ApiResponse(code = 401, message = HttpStatusMessages.MESSAGE_401),
            @ApiResponse(code = 403, message = HttpStatusMessages.MESSAGE_403) })
    public Response createBucket(final Bucket bucket) {
        authorizeAccess(RequestAction.WRITE);
        final Bucket createdBucket = registryService.createBucket(bucket);
        linkService.populateBucketLinks(createdBucket);
        return Response.status(Response.Status.OK).entity(createdBucket).build();
    }

    @GET
    @Consumes(MediaType.WILDCARD)
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(
            value = "Gets all buckets",
            notes = "The returned list will include only buckets for which the caller is authorized.",
            response = Bucket.class,
            responseContainer = "List"
    )
    @ApiResponses({
            @ApiResponse(code = 400, message = HttpStatusMessages.MESSAGE_400),
            @ApiResponse(code = 401, message = HttpStatusMessages.MESSAGE_401),
            @ApiResponse(code = 403, message = HttpStatusMessages.MESSAGE_403) })
    public Response getBuckets(
            @ApiParam(value = SortParameter.API_PARAM_DESCRIPTION, format = "field:order", allowMultiple = true, example = "name:ASC")
            @QueryParam("sort")
            final List<String> sortParameters) {
        authorizeAccess(RequestAction.READ);

        final Set<String> authorizedBucketIds = getAuthorizedBucketIds();

        if (authorizedBucketIds == null || authorizedBucketIds.isEmpty()) {
            // not authorized for any bucket, return empty list of items
            return Response.status(Response.Status.OK).entity(new ArrayList<BucketItem>()).build();
        }

        final QueryParameters.Builder paramsBuilder = new QueryParameters.Builder();
        for (String sortParam : sortParameters) {
            paramsBuilder.addSort(SortParameter.fromString(sortParam));
        }

        final List<Bucket> buckets = registryService.getBuckets(paramsBuilder.build(), authorizedBucketIds);
        linkService.populateBucketLinks(buckets);

        return Response.status(Response.Status.OK).entity(buckets).build();
    }

    @GET
    @Path("{bucketId}")
    @Consumes(MediaType.WILDCARD)
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(
            value = "Gets a bucket",
            response = Bucket.class
    )
    @ApiResponses({
            @ApiResponse(code = 401, message = HttpStatusMessages.MESSAGE_401),
            @ApiResponse(code = 403, message = HttpStatusMessages.MESSAGE_403),
            @ApiResponse(code = 404, message = HttpStatusMessages.MESSAGE_404) })
    public Response getBucket(
            @PathParam("bucketId")
            @ApiParam("The bucket identifier")
                    final String bucketId) {

        authorizeBucketAccess(RequestAction.READ, bucketId);
        final Bucket bucket = registryService.getBucket(bucketId);
        linkService.populateBucketLinks(bucket);

        if (bucket.getVersionedFlows() != null) {
            linkService.populateFlowLinks(bucket.getVersionedFlows());
        }

        return Response.status(Response.Status.OK).entity(bucket).build();
    }

    @PUT
    @Path("{bucketId}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(
            value = "Updates a bucket",
            response = Bucket.class
    )
    @ApiResponses({
            @ApiResponse(code = 400, message = HttpStatusMessages.MESSAGE_400),
            @ApiResponse(code = 401, message = HttpStatusMessages.MESSAGE_401),
            @ApiResponse(code = 403, message = HttpStatusMessages.MESSAGE_403),
            @ApiResponse(code = 404, message = HttpStatusMessages.MESSAGE_404),
            @ApiResponse(code = 409, message = HttpStatusMessages.MESSAGE_409) })
    public Response updateBucket(
            @PathParam("bucketId")
            @ApiParam("The bucket identifier")
            final String bucketId, final Bucket bucket) {

        if (StringUtils.isBlank(bucketId)) {
            throw new BadRequestException("Bucket id cannot be blank");
        }

        if (bucket == null) {
            throw new BadRequestException("Bucket cannot be null");
        }

        if (bucket.getIdentifier() != null && !bucketId.equals(bucket.getIdentifier())) {
            throw new BadRequestException("Bucket id in path param must match bucket id in body");
        } else {
            bucket.setIdentifier(bucketId);
        }

        authorizeBucketAccess(RequestAction.WRITE, bucketId);

        final Bucket updatedBucket = registryService.updateBucket(bucket);
        linkService.populateBucketLinks(updatedBucket);
        return Response.status(Response.Status.OK).entity(updatedBucket).build();
    }

    @DELETE
    @Path("{bucketId}")
    @Consumes(MediaType.WILDCARD)
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(
            value = "Deletes a bucket along with all objects stored in the bucket",
            response = Bucket.class
    )
    @ApiResponses({
            @ApiResponse(code = 400, message = HttpStatusMessages.MESSAGE_400),
            @ApiResponse(code = 401, message = HttpStatusMessages.MESSAGE_401),
            @ApiResponse(code = 403, message = HttpStatusMessages.MESSAGE_403),
            @ApiResponse(code = 404, message = HttpStatusMessages.MESSAGE_404) })
    public Response deleteBucket(
            @PathParam("bucketId")
            @ApiParam("The bucket identifier")
            final String bucketId) {

        if (StringUtils.isBlank(bucketId)) {
            throw new BadRequestException("Bucket id cannot be blank");
        }
        authorizeBucketAccess(RequestAction.DELETE, bucketId);
        final Bucket deletedBucket = registryService.deleteBucket(bucketId);
        return Response.status(Response.Status.OK).entity(deletedBucket).build();
    }

    @GET
    @Path("fields")
    @Consumes(MediaType.WILDCARD)
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(
            value = "Retrieves field names for searching or sorting on buckets.",
            response = Fields.class
    )
    public Response getAvailableBucketFields() {
        final Set<String> bucketFields = registryService.getBucketFields();
        final Fields fields = new Fields(bucketFields);
        return Response.status(Response.Status.OK).entity(fields).build();
    }

    private void authorizeAccess(RequestAction actionType) {
        authorizationService.authorizeAccess(lookup -> {
            final Authorizable bucketsAuthorizable = lookup.getBucketsAuthorizable();
            bucketsAuthorizable.authorize(authorizer, actionType, NiFiUserUtils.getNiFiUser());
        });
    }

}