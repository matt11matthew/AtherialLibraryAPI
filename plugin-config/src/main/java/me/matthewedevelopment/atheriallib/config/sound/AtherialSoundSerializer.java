package me.matthewedevelopment.atheriallib.config.sound;

import me.matthewedevelopment.atheriallib.config.yaml.ConfigSerializable;
import me.matthewedevelopment.atheriallib.config.yaml.SerializeType;

import java.util.HashMap;
import java.util.Map;

public class AtherialSoundSerializer implements ConfigSerializable<AtherialSound> {

    @Override
    public AtherialSound deserializeComplex(Map<String, Object> map) {

        String sound = (String) map.get("sound");
        float pitch = Float.parseFloat(String.valueOf((double) map.get("pitch")));
        float volume = Float.parseFloat(String.valueOf((double) map.get("volume")));
        boolean enabled = map.containsKey("enabled")? (boolean) map.get("enabled") :true;
        return new AtherialSound(sound, volume,pitch, enabled);
    }

    @Override
    public Map<String, Object> serializeComplex(AtherialSound object) {
        Map<String, Object> map =new HashMap<>();
        map.put("sound", object.getSound());
        map.put("volume", object.getVolume());

        map.put("pitch", object.getPitch());

        if (!object.isEnabled()){
            map.put("enabled", false);
        }
        return map;
    }

    @Override
    public SerializeType getComplexity() {
        return SerializeType.COMPLEX;
    }
}
