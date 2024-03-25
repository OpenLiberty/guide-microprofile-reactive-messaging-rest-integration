// tag::copyright[]
/*******************************************************************************
 * Copyright (c) 2020, 2024 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
// end::copyright[]
package io.openliberty.guides.inventory;

import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.eclipse.microprofile.reactive.messaging.Outgoing;
import org.reactivestreams.Publisher;

import io.openliberty.guides.models.PropertyMessage;
import io.openliberty.guides.models.SystemLoad;
import io.reactivex.rxjava3.core.BackpressureStrategy;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.FlowableEmitter;


@ApplicationScoped
//tag::inventoryEndPoint[]
@Path("/inventory")
//end::inventoryEndPoint[]
public class InventoryResource {

    private static Logger logger = Logger.getLogger(InventoryResource.class.getName());
    // tag::flowableEmitterDecl[]
    private FlowableEmitter<String> propertyNameEmitter;
    // end::flowableEmitterDecl[]

    @Inject
    private InventoryManager manager;

    @GET
    @Path("/systems")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getSystems() {
        List<Properties> systems = manager.getSystems()
                                          .values()
                                          .stream()
                                          .collect(Collectors.toList());
        return Response.status(Response.Status.OK)
                       .entity(systems)
                       .build();
    }

    @GET
    @Path("/systems/{hostname}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getSystem(@PathParam("hostname") String hostname) {
        Optional<Properties> system = manager.getSystem(hostname);
        if (system.isPresent()) {
            return Response.status(Response.Status.OK)
                           .entity(system)
                           .build();
        }
        return Response.status(Response.Status.NOT_FOUND)
                       .entity("hostname does not exist.")
                       .build();
    }

    // tag::updateSystemProperty[]
    // tag::annotatedPut[]
    @PUT
    // end::annotatedPut[]
    // tag::putPath[]
    @Path("/data")
    // end::putPath[]
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.TEXT_PLAIN)
    public Response updateSystemProperty(String propertyName) {
        logger.info("updateSystemProperty: " + propertyName);
        // tag::flowableEmitter[]
        propertyNameEmitter.onNext(propertyName);
        // end::flowableEmitter[]
        return Response
                 .status(Response.Status.OK)
                 .entity("Request successful for the " + propertyName + " property\n")
                 .build();
    }
    // end::updateSystemProperty[]

    @DELETE
    @Produces(MediaType.APPLICATION_JSON)
    public Response resetSystems() {
        manager.resetSystems();
        return Response.status(Response.Status.OK)
                       .build();
    }

    // tag::updateStatus[]
    // tag::systemLoad[]
    @Incoming("systemLoad")
    // end::systemLoad[]
    public void updateStatus(SystemLoad sl)  {
        String hostname = sl.hostname;
        if (manager.getSystem(hostname).isPresent()) {
            manager.updateCpuStatus(hostname, sl.loadAverage);
            logger.info("Host " + hostname + " was updated: " + sl);
        } else {
            manager.addSystem(hostname, sl.loadAverage);
            logger.info("Host " + hostname + " was added: " + sl);
        }
    }
    // end::updateStatus[]

    // tag::propertyMessage[]
    @Incoming("addSystemProperty")
    // end::propertyMessage[]
    public void getPropertyMessage(PropertyMessage pm)  {
        logger.info("getPropertyMessage: " + pm);
        String hostId = pm.hostname;
        if (manager.getSystem(hostId).isPresent()) {
            manager.updatePropertyMessage(hostId, pm.key, pm.value);
            logger.info("Host " + hostId + " was updated: " + pm);
        } else {
            manager.addSystem(hostId, pm.key, pm.value);
            logger.info("Host " + hostId + " was added: " + pm);
        }
    }

    // tag::sendPropertyName[]
    // tag::OutgoingPropertyName[]
    @Outgoing("requestSystemProperty")
    // end::OutgoingPropertyName[]
    public Publisher<String> sendPropertyName() {
        // tag::flowableCreate[]
        Flowable<String> flowable = Flowable.<String>create(emitter ->
            this.propertyNameEmitter = emitter, BackpressureStrategy.BUFFER);
        // end::flowableCreate[]
        return flowable;
    }
    // end::sendPropertyName[]
}
