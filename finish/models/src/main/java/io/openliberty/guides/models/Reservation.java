package io.openliberty.guides.models;

import java.util.Objects;

import javax.json.bind.Jsonb;
import javax.json.bind.JsonbBuilder;

import org.apache.kafka.common.serialization.Deserializer;
import org.apache.kafka.common.serialization.Serializer;

public class Reservation {

    private static final Jsonb jsonb = JsonbBuilder.create();

    public String username;
    public int duration;
    public String hostname;
    public long reservedTime;

    

    

    @Override
    public int hashCode() {
        return Objects.hash(username, duration);
    }

    @Override
    public String toString() {
        return "Reservation: " + jsonb.toJson(this);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Reservation)) return false;
        Reservation r = (Reservation) o;
        return Objects.equals(username, r.username)
                && Objects.equals(duration, r.duration);
    }

    public static class ReservationMessageSerializer implements Serializer<Object> {
        @Override
        public byte[] serialize(String topic, Object data) {
            return jsonb.toJson(data).getBytes();
        }
    }

    public static class ReservationMessageDeserializer implements Deserializer<Reservation> {
        @Override
        public Reservation deserialize(String topic, byte[] data) {
            if (data == null)
                return null;
            return jsonb.fromJson(new String(data), Reservation.class);
        }
    }




}
