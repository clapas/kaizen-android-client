package com.pyco.appkaizen;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

//import org.apache.harmony.javax.security.sasl.Sasl;
import de.measite.smack.Sasl;

import org.apache.harmony.javax.security.sasl.SaslException;
import org.apache.harmony.javax.security.auth.callback.CallbackHandler;
import org.jivesoftware.smack.SmackException.NotConnectedException;

import org.jivesoftware.smack.SASLAuthentication;
import org.jivesoftware.smack.XMPPException;
import org.jivesoftware.smack.sasl.SASLMechanism;
import org.jivesoftware.smack.util.Base64;
import org.jivesoftware.smack.packet.Packet;

public class SASLGoogleOAuth2Mechanism extends SASLMechanism {

    private static final Logger log = Logger.getLogger("XMPPChatDemoActivity");
    public static final String NAME = "X-OAUTH2";

    public SASLGoogleOAuth2Mechanism(SASLAuthentication saslAuthentication) {
        super(saslAuthentication);
        log.info("Creating SASL mechanism for GTalk (X-OAUTH2)");
    }

    @Override
    public void authenticate(String username, String host, String serviceName, String password) throws IOException, SaslException, NotConnectedException {
        this.authenticationId = username;
        this.hostname = host;
        this.password = password;

        String[] mechanisms = { "PLAIN" };
        Map<String, String> props = new HashMap<String, String>();
        this.sc = Sasl.createSaslClient(mechanisms, username, "xmpp", host, props, this);
        authenticate();
    }
    /*
    @Override
    public void authenticate(String host, CallbackHandler cbh) throws IOException, SaslException, NotConnectedException {
	String[] mechanisms = { "PLAIN" };
	Map<String, String> props = new HashMap<String, String>();

	sc = Sasl.createSaslClient(mechanisms, null, "xmpp", host, props, cbh);
	authenticate();
    }
    */
    @Override
    protected void authenticate() throws IOException, SaslException, NotConnectedException {
	String authenticationText = null;

	try {
	    if (sc.hasInitialResponse()) {
		byte[] response = sc.evaluateChallenge(new byte[0]);
		authenticationText = Base64.encodeBytes(response, Base64.DONT_BREAK_LINES);
	    }
	} catch (SaslException e) {
	    throw new SaslException("SASL authentication failed", e);
	}

	// Send the authentication to the server
	getSASLAuthentication().send(new GoogleOAuthMechanism(authenticationText));
    }
    @Override
    protected String getName() {
        return NAME;
    }

    /**
     * Initiating SASL authentication by select a mechanism.
     */
    public static class GoogleOAuthMechanism extends Packet {
	private final String authenticationText;

	/**
	 * Create a GoogleOAuthMechanism.
	 *
	 * @param authenticationText the authentification token
	 *
	 */
	public GoogleOAuthMechanism(final String authenticationText) {
	    this.authenticationText = authenticationText;
	}

	@Override
	public String toXML() {
	    StringBuilder stanza = new StringBuilder();

	    stanza.append("<auth mechanism=\"").append(NAME);
	    stanza.append("\" xmlns=\"urn:ietf:params:xml:ns:xmpp-sasl\" "
		    + "auth:service=\"oauth2\" "
		    + "xmlns:auth=\"http://www.google.com/talk/protocol/auth\">");
	    if (authenticationText != null
		    && authenticationText.trim().length() > 0) {
		stanza.append(authenticationText);
	    }
	    stanza.append("</auth>");
	    return stanza.toString();
	}
    }
}
