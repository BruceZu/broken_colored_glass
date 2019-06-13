import java.io.IOException;
import java.io.InputStream;
import org.tinyradius.dictionary.Dictionary;
import org.tinyradius.dictionary.DictionaryParser;
import org.tinyradius.dictionary.MemoryDictionary;
import org.tinyradius.dictionary.WritableDictionary;

/**
 * user case: 
  <code> RadiusClient rc = new RadiusClient(ipAddress, secretKey);
    AccessRequest ar = new AccessRequest(user, password);
    ar.setDictionary(SingleTon.INSTANCE.getFPCDictionary());
    rc.setAuthPort(port);
    log.info("AUTH USED === " + ar.getAuthProtocol());

    try {
      response = rc.authenticate(ar);
      if (response.getPacketType() == RadiusPacket.ACCESS_ACCEPT) {
        // authenticated
      } else {
        // failed
      }
    } catch (IOException | RadiusException e) {
      //
    }</code>
   
    $ find -type f -name ProRadiusDictionary
     ./src/main/resources/ProRadiusDictionary
     ./project/target/fpc/WEB-INF/classes/ProRadiusDictionary

################################################
# Attributes
################################################
...
################################################
# Attribute values
################################################
...
################################################
# VENDOR Attributes
################################################
VENDOR		  	12356	  Proj

VENDORATTR		12356   Proj-User-Role             40    string
VENDORATTR		12356   Proj-Tenant-Identification 41    string
VENDORATTR		12356   Proj-Tenant-User-Sites     42    string
 */
public enum SingleTonRadiusDirectory {
  INSTANCE;
  private WritableDictionary wrappedDict = new MemoryDictionary();

  SingleTonRadiusDirectory() {
    try (InputStream source =
        getClass().getClassLoader().getResourceAsStream("ProRadiusDictionary"); ) {
      DictionaryParser.parseDictionary(source, wrappedDict);
    } catch (IOException e) {
      throw new RuntimeException("Dictionary unavailable", e);
    }
  }

  public Dictionary getFPCDictionary() {
    return wrappedDict;
  }
}
