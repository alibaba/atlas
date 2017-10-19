package com.taobao.android.tpatch.manifest;

import android.content.res.AXMLResource;
import org.apache.commons.io.IOUtils;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 */
public class AXMLPrint {
    public static String decodeManifest(File manifestFile){
        FileInputStream in = null;

        try {
            AXMLResource axmlResource = new AXMLResource();
            in = new FileInputStream(manifestFile);
            axmlResource.read(in);
            return axmlResource.toXmlString();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            IOUtils.closeQuietly(in);

        }
        return null;
    }

    public static Manifest paresManfiest(File manifestFile) throws JAXBException {
        String manifestXml =  AXMLPrint.decodeManifest(manifestFile);
        JAXBContext context = JAXBContext.newInstance(Manifest.class);
        Unmarshaller unmarshaller = context.createUnmarshaller();
        InputStream stream = new ByteArrayInputStream(manifestXml.getBytes(StandardCharsets.UTF_8));
        Manifest manifest = (Manifest) unmarshaller.unmarshal(stream);
        return manifest;
    }

}
