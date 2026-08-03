package com.daertech.platform.configuration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

@Component
public class SecretCipher {
    private final SecretKeySpec key;
    private final SecureRandom random=new SecureRandom();
    public SecretCipher(@Value("${app.config.encryption-key}") String raw){
        if(raw==null||raw.length()<32) throw new IllegalStateException("APP_CONFIG_ENCRYPTION_KEY debe tener al menos 32 caracteres");
        try{key=new SecretKeySpec(MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8)),"AES");}
        catch(Exception e){throw new IllegalStateException(e);}
    }
    public String encrypt(String value){
        if(value==null) return null;
        try{byte[] iv=new byte[12];random.nextBytes(iv);Cipher c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.ENCRYPT_MODE,key,new GCMParameterSpec(128,iv));byte[] enc=c.doFinal(value.getBytes(StandardCharsets.UTF_8));byte[] all=new byte[iv.length+enc.length];System.arraycopy(iv,0,all,0,iv.length);System.arraycopy(enc,0,all,iv.length,enc.length);return Base64.getEncoder().encodeToString(all);}catch(Exception e){throw new IllegalStateException("No fue posible cifrar el secreto",e);}
    }
    public String decrypt(String value){
        if(value==null) return null;
        try{byte[] all=Base64.getDecoder().decode(value);byte[] iv=java.util.Arrays.copyOfRange(all,0,12);byte[] enc=java.util.Arrays.copyOfRange(all,12,all.length);Cipher c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.DECRYPT_MODE,key,new GCMParameterSpec(128,iv));return new String(c.doFinal(enc),StandardCharsets.UTF_8);}catch(Exception e){throw new IllegalStateException("No fue posible descifrar el secreto",e);}
    }
}
