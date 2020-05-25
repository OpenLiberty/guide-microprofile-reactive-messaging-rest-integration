// tag::copyright[]
/*******************************************************************************
 * Copyright (c) 2020 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - Initial implementation
 *******************************************************************************/
// end::copyright[]
package io.openliberty.guides.inventory;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.TreeMap;

import javax.enterprise.context.ApplicationScoped;

import io.openliberty.guides.models.Reservation;

@ApplicationScoped
public class InventoryManager {

    private Map<String, Properties> systems = Collections.synchronizedMap(new TreeMap<String, Properties>());
    private Map<String, ArrayList<Properties>> reservations = Collections.synchronizedMap
            (new TreeMap<String, ArrayList<Properties>>());
    DateFormat dateFormat = new SimpleDateFormat("hh.mm aa");


    public void addSystem(String hostname, Double systemLoad) {
        if (!systems.containsKey(hostname)) {
            Properties p = new Properties();
            p.put("hostname", hostname);
            p.put("systemLoad", systemLoad);
            systems.put(hostname, p);
        }
    }

    public void addSystem(String hostname, String key, String value) {
        if (!systems.containsKey(hostname)) {
            Properties p = new Properties();
            p.put("hostname", hostname);
            p.put("key", value);
            systems.put(hostname, p);
        }
    }
    
    

    public void updateCpuStatus(String hostname, Double systemLoad) {
        Optional<Properties> p = getSystem(hostname);
        if (p.isPresent()) {
            if (p.get().getProperty(hostname) == null && hostname != null)
                p.get().put("systemLoad", systemLoad);
        }
    }

    public void updatePropertyMessage(String hostname, String key, String value) {
        Optional<Properties> p = getSystem(hostname);
        if (p.isPresent()) {
            if (p.get().getProperty(hostname) == null && hostname != null) {
                p.get().put(key, value);
            }
        }
    }
    
    public void updateReservation(String hostname, Reservation res) {
        Optional<ArrayList<Properties>> p = getReservation(hostname);
        Properties newProperty = new Properties();
        newProperty.put("username", res.username);
        newProperty.put("reservedStartTime", dateFormat.format(new Date(res.reservedTime)).toString());
        newProperty.put("duration", res.duration);
        if (p.isPresent()) {
            p.get().add(newProperty);
        }
    }
    
    public void addReservation(String hostname, Reservation res) {
        if (!reservations.containsKey(hostname)) {
            ArrayList<Properties> newReservation = new ArrayList<Properties>();
            Properties newProperty = new Properties();
            newProperty.put("username", res.username);
            newProperty.put("reservedStartTime", dateFormat.format(new Date(res.reservedTime)).toString());
            newProperty.put("duration", res.duration);
            newReservation.add(newProperty);
            reservations.put(hostname, newReservation);
        }
    }
    
    public Optional<ArrayList<Properties>> getReservation(String hostname) {
        ArrayList<Properties> p = reservations.get(hostname);
        return Optional.ofNullable(p);
    }

    public Optional<Properties> getSystem(String hostname) {
        Properties p = systems.get(hostname);
        return Optional.ofNullable(p);
    }

    public Map<String, Properties> getSystems() {
        return new TreeMap<>(systems);
    }
    
    public Map<String, ArrayList<Properties>> getReservations() {
        return new TreeMap<>(reservations);
    }

    public void resetSystems() {
        systems.clear();
    }
}