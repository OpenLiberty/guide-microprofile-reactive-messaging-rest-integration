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
package it.io.openliberty.guides.system;

import java.time.Duration;
import java.util.Collections;
import java.util.Properties;
import java.io.IOException;
import java.nio.file.Paths;
import java.net.Socket;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.testcontainers.junit.jupiter.Testcontainers;
import static org.junit.Assert.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.ImageFromDockerfile;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.utility.DockerImageName;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.apache.kafka.common.serialization.StringDeserializer;

import io.openliberty.guides.models.PropertyMessage;
import io.openliberty.guides.models.PropertyMessage.PropertyMessageDeserializer;
import io.openliberty.guides.models.SystemLoad;
import io.openliberty.guides.models.SystemLoad.SystemLoadDeserializer;

@Testcontainers
public class SystemServiceIT {

    private static Logger logger = LoggerFactory.getLogger(SystemServiceIT.class);

    private static Network network = Network.newNetwork();

    public static KafkaConsumer<String, SystemLoad> consumer;

    public static KafkaConsumer<String, PropertyMessage> propertyConsumer;

    public static KafkaProducer<String, String> propertyProducer;

    private static ImageFromDockerfile systemImage =
        new ImageFromDockerfile("system:1.0-SNAPSHOT")
            .withDockerfile(Paths.get("./Dockerfile"));

    private static KafkaContainer kafkaContainer =
        new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:latest"))
            .withListener(() -> "kafka:19092")
            .withNetwork(network);

    private static GenericContainer<?> systemContainer =
        new GenericContainer(systemImage)
            .withNetwork(network)
            .withExposedPorts(9083)
            .waitingFor(Wait.forHttp("/health/ready").forPort(9083))
            .withStartupTimeout(Duration.ofMinutes(2))
            .withLogConsumer(new Slf4jLogConsumer(logger))
            .dependsOn(kafkaContainer);

    private static boolean isServiceRunning(String host, int port) {
        try {
            Socket socket = new Socket(host, port);
            socket.close();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @BeforeAll
    public static void startContainers() {
        if (isServiceRunning("localhost", 9083)) {
            System.out.println("Testing with mvn liberty:devc");
        } else {
            System.out.println("Testing with mvn verify");
            kafkaContainer.start();
            systemContainer.withEnv(
                "mp.messaging.connector.liberty-kafka.bootstrap.servers",
                "kafka:19092");
            systemContainer.start();
        }
    }

    @BeforeEach
    public void setUp() {
        Properties consumerProps = new Properties();
        if (isServiceRunning("localhost", 9083)) {
            consumerProps.put(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,
                "localhost:9094");
        } else {
            consumerProps.put(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,
                kafkaContainer.getBootstrapServers());
        }
        consumerProps.put(
            ConsumerConfig.GROUP_ID_CONFIG,
            "system-load-status");
        consumerProps.put(
            ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,
            StringDeserializer.class.getName());
        consumerProps.put(
            ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
            SystemLoadDeserializer.class.getName());
        consumerProps.put(
            ConsumerConfig.AUTO_OFFSET_RESET_CONFIG,
            "earliest");

        consumer = new KafkaConsumer<String, SystemLoad>(consumerProps);
        consumer.subscribe(Collections.singletonList("system.load"));

        Properties propertyConsumerProps = new Properties();
        if (isServiceRunning("localhost", 9083)) {
            propertyConsumerProps.put(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,
                "localhost:9094");
        } else {
            propertyConsumerProps.put(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,
                kafkaContainer.getBootstrapServers());
        }
        propertyConsumerProps.put(
            ConsumerConfig.GROUP_ID_CONFIG,
            "property-name");
        propertyConsumerProps.put(
            ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,
            StringDeserializer.class.getName());
        propertyConsumerProps.put(
            ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
            PropertyMessageDeserializer.class.getName());
        propertyConsumerProps.put(
            ConsumerConfig.AUTO_OFFSET_RESET_CONFIG,
            "earliest");

        propertyConsumer =
            new KafkaConsumer<String, PropertyMessage>(propertyConsumerProps);
        propertyConsumer.subscribe(Collections.singletonList("add.system.property"));

        Properties producerProps = new Properties();
        if (isServiceRunning("localhost", 9083)) {
            producerProps.put(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG,
                "localhost:9094");
        } else {
            producerProps.put(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG,
                kafkaContainer.getBootstrapServers());
        }
        producerProps.put(
            ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG,
            StringSerializer.class.getName());
        producerProps.put(
            ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG,
            StringSerializer.class.getName());

        propertyProducer = new KafkaProducer<String, String>(producerProps);
    }


    @AfterAll
    public static void stopContainers() {
        systemContainer.stop();
        kafkaContainer.stop();
        network.close();
    }

    @AfterEach
    public void tearDown() {
        consumer.close();
        propertyConsumer.close();
        propertyProducer.close();
    }

    @Test
    public void testCpuStatus() {
        ConsumerRecords<String, SystemLoad> records =
            consumer.poll(Duration.ofMillis(30 * 1000));
        System.out.println(
            "Polled the consumer " + records.count() + " records from Kafka:");
        assertTrue(records.count() > 0, "No consumer records processed");

        for (ConsumerRecord<String, SystemLoad> record : records) {
            SystemLoad sl = record.value();
            System.out.println(sl);
            assertNotNull(sl.hostname);
            assertNotNull(sl.loadAverage);
        }
        consumer.commitAsync();
    }

    @Test
    public void testPropertyMessage() throws IOException, InterruptedException {
        propertyProducer.send(
            new ProducerRecord<String, String>("request.system.property", "os.name"));

        ConsumerRecords<String, PropertyMessage> records =
            propertyConsumer.poll(Duration.ofMillis(30 * 1000));
        System.out.println(
            "Polled the propertyConsumer " + records.count() + " records from Kafka:");
        assertTrue(records.count() > 0, "No propertyConsumer records processed");

        for (ConsumerRecord<String, PropertyMessage> record : records) {
            PropertyMessage pm = record.value();
            System.out.println(pm);
            assertNotNull(pm.hostname);
            assertEquals("os.name", pm.key);
            assertNotNull(pm.value);
        }
        consumer.commitAsync();
    }
}
