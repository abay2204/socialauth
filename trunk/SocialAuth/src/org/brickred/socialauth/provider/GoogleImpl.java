/*
 ===========================================================================
 Copyright (c) 2010 BrickRed Technologies Limited

 Permission is hereby granted, free of charge, to any person obtaining a copy
 of this software and associated documentation files (the "Software"), to deal
 in the Software without restriction, including without limitation the rights
 to use, copy, modify, merge, publish, distribute, sub-license, and/or sell
 copies of the Software, and to permit persons to whom the Software is
 furnished to do so, subject to the following conditions:

 The above copyright notice and this permission notice shall be included in
 all copies or substantial portions of the Software.

 THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 THE SOFTWARE.
 ===========================================================================

 */

package org.brickred.socialauth.provider;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import javax.servlet.http.HttpServletRequest;

import org.brickred.socialauth.AbstractProvider;
import org.brickred.socialauth.AuthProvider;
import org.brickred.socialauth.Contact;
import org.brickred.socialauth.Profile;
import org.brickred.socialauth.exception.ProviderStateException;
import org.brickred.socialauth.exception.SocialAuthConfigurationException;
import org.brickred.socialauth.exception.SocialAuthException;
import org.brickred.socialauth.util.XMLParseUtil;
import org.openid4java.consumer.ConsumerException;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import com.dyuproject.oauth.Endpoint;
import com.dyuproject.oauth.HttpAuthTransport;
import com.dyuproject.oauth.NonceAndTimestamp;
import com.dyuproject.oauth.Signature;
import com.dyuproject.oauth.SimpleNonceAndTimestamp;
import com.dyuproject.oauth.Token;
import com.dyuproject.oauth.TokenExchange;
import com.dyuproject.oauth.Transport;
import com.dyuproject.openid.OpenIdServletFilter;
import com.dyuproject.openid.OpenIdUser;
import com.dyuproject.openid.RelyingParty;
import com.dyuproject.openid.YadisDiscovery;
import com.dyuproject.openid.RelyingParty.ListenerCollection;
import com.dyuproject.openid.ext.AxSchemaExtension;
import com.dyuproject.openid.ext.SRegExtension;
import com.dyuproject.util.http.HttpConnector;
import com.dyuproject.util.http.SimpleHttpConnector;
import com.dyuproject.util.http.UrlEncodedParameterMap;
import com.dyuproject.util.http.HttpConnector.Parameter;
import com.dyuproject.util.http.HttpConnector.Response;

/**
 * Provider implementation for Google
 * 
 * @author abhinavm@brickred.com
 * 
 */
public class GoogleImpl extends AbstractProvider implements AuthProvider
{

	static final String GOOGLE_IDENTIFIER = "https://www.google.com/accounts/o8/id";
	static final String GOOGLE_OPENID_SERVER = "https://www.google.com/accounts/o8/ud";
	static final String OAUTH_SCOPE = "http://www.google.com/m8/feeds/";
	final Endpoint __google;
	final String CONTACTS_FEED_URL = "http://www.google.com/m8/feeds/contacts/default/full/?max-results=1000";
	private OpenIdUser user;
	private Token token;
	private ListenerCollection listeners;

	public GoogleImpl(final Properties props) throws ConsumerException {
		__google = Endpoint.load(props, "www.google.com");

		listeners = new ListenerCollection();
		listeners.addListener(new SRegExtension().addExchange(EMAIL)
				.addExchange(COUNTRY).addExchange(LANGUAGE).addExchange(
						FULL_NAME).addExchange(NICK_NAME).addExchange(DOB)
						.addExchange(GENDER).addExchange(POSTCODE));
		listeners.addListener(new AxSchemaExtension().addExchange(EMAIL)
				.addExchange(FIRST_NAME).addExchange(LAST_NAME).addExchange(
						COUNTRY).addExchange(LANGUAGE).addExchange(FULL_NAME)
						.addExchange(NICK_NAME).addExchange(DOB).addExchange(GENDER)
						.addExchange(POSTCODE));
		listeners.addListener(new RelyingParty.Listener() {
			public void onDiscovery(final OpenIdUser user,
					final HttpServletRequest request) {
				System.err.println("discovered user: " + user.getClaimedId());
			}

			public void onPreAuthenticate(final OpenIdUser user,
					final HttpServletRequest request,
					final UrlEncodedParameterMap params) {

				params.add("openid.ns.oauth", EXT_NAMESPACE);
				params.put("openid.oauth.consumer", __google.getConsumerKey());
				params.put("openid.oauth.scope", OAUTH_SCOPE);

				System.err.println("pre-authenticate user: "
						+ user.getClaimedId());

			}

			public void onAuthenticate(final OpenIdUser user,
					final HttpServletRequest request) {

			}

			public void onAccess(final OpenIdUser user,
					final HttpServletRequest request) {
				System.err.println("user access: " + user.getIdentity());
				System.err.println("info: " + user.getAttribute("info"));
			}
		});
	}

	public String getLoginRedirectURL(final String returnTo) throws IOException {
		setProviderState(true);
		String errorMsg = OpenIdServletFilter.DEFAULT_ERROR_MSG;
		try {

			RelyingParty _relyingParty = RelyingParty.getInstance();
			// we expect it to be google so skip discovery to speed up the
			// openid process
			user = OpenIdUser.populate(GOOGLE_IDENTIFIER,
					YadisDiscovery.IDENTIFIER_SELECT, GOOGLE_OPENID_SERVER);
			user.setAttribute("google_scope", OAUTH_SCOPE);
			user.setAttribute("google_type", "contacts");

			// associate and authenticate user
			StringBuffer url = new StringBuffer(returnTo);
			String trustRoot = url.substring(0, url.indexOf("/", 9));
			String realm = url.substring(0, url.lastIndexOf("/"));

			_relyingParty.getOpenIdContext().getAssociation().associate(user,
					_relyingParty.getOpenIdContext());
			UrlEncodedParameterMap params = RelyingParty.getAuthUrlMap(user,
					trustRoot,
					realm, returnTo);
			listeners.onPreAuthenticate(user, null, params);

			return params.toStringRFC3986();



		} catch (Exception e) {
			e.printStackTrace();
			errorMsg = OpenIdServletFilter.DEFAULT_ERROR_MSG;
		}
		return null;
	}




	public Profile verifyResponse(final HttpServletRequest request)
	throws Exception {
		if (!isProviderState()) {
			throw new ProviderStateException();
		}
		try {
			RelyingParty _relyingParty = RelyingParty.getInstance();
			request.setAttribute(OpenIdUser.ATTR_NAME, user);
			user = _relyingParty.discover(request);

			if (user.isAssociated() && RelyingParty.isAuthResponse(request)) {
				// verify authentication

				if (_relyingParty.getOpenIdContext().getAssociation()
						.verifyAuth(user,
								RelyingParty.getAuthParameters(request),
								_relyingParty.getOpenIdContext()))
				{
					listeners.onAuthenticate(this.user, request);

					Map<String, String> sreg = SRegExtension.remove(user);
					Map<String, String> axschema = AxSchemaExtension
					.remove(user);
					if (sreg != null && !sreg.isEmpty()) {
						System.err.println("sreg: " + sreg);
						user.setAttribute("info", sreg);
					} else if (axschema != null && !axschema.isEmpty()) {
						System.err.println("axschema: " + axschema);
						user.setAttribute("info", axschema);
					}
					String alias = user.getExtension(EXT_NAMESPACE);
					if (alias != null) {
						String requestToken = request
						.getParameter("openid." + alias
								+ ".request_token");
						token = new Token(__google.getConsumerKey(),
								requestToken, null, Token.AUTHORIZED);
						UrlEncodedParameterMap accessTokenParams = new UrlEncodedParameterMap();
						try {
							Response accessTokenResponse = fetchToken(
									TokenExchange.ACCESS_TOKEN,
									accessTokenParams, __google, token);
							if (accessTokenResponse.getStatus() == 200
									&& token.getState() == Token.ACCESS_TOKEN) {
								user
								.setAttribute("token_k", token
										.getKey());
								user.setAttribute("token_s", token
										.getSecret());

							}
						} catch (IOException e) {
							throw new SocialAuthException(
									"Unable to retrieve the token", e);
						}
					}

					Map<String, String> info = (Map<String, String>) user
					.getAttribute("info");

					Profile p = new Profile();
					p.setEmail(info.get(EMAIL));
					p.setFirstName(info.get(FIRST_NAME));
					p.setLastName(info.get(LAST_NAME));
					p.setCountry(info.get(COUNTRY));
					p.setLanguage(info.get(LANGUAGE));
					p.setFullName(info.get(FULL_NAME));
					p.setDisplayName(info.get(NICK_NAME));
					p.setLocation(info.get(POSTCODE));
					p.setDob(info.get(DOB));
					p.setGender(info.get(GENDER));
					p.setValidatedId(user.getIdentifier());
					return p;
				}
			}
		} catch (Exception e) {
			throw new SocialAuthException(e);
		}
		return null;
	}

	public void updateStatus(final String msg) {
		System.out.println("WARNING: Not implemented");
	}

	public List<Contact> getContactList() throws Exception {
		if(token == null){
			throw new SocialAuthConfigurationException(
			"Either you dont have permission to get the contacts OR application keys are wrong in properties file");
		}
		UrlEncodedParameterMap serviceParams = new UrlEncodedParameterMap(
				CONTACTS_FEED_URL);
		NonceAndTimestamp nts = SimpleNonceAndTimestamp.getDefault();
		Signature sig = __google.getSignature();

		HttpConnector connector = SimpleHttpConnector.getDefault();

		Parameter authorizationHeader = new Parameter("Authorization",
				HttpAuthTransport.getAuthHeaderValue(serviceParams, __google,
						token, nts, sig));

		List<Contact> plist = new ArrayList<Contact>();
		Response serviceResponse;
		Element root;
		try {
			serviceResponse = connector.doGET(serviceParams
					.toStringRFC3986(), authorizationHeader);
		} catch (IOException ie) {
			throw ie;
		}

		try {
			root = XMLParseUtil.loadXmlResource(serviceResponse
					.getInputStream());
		} catch (Exception e) {
			throw new SocialAuthException(
					"Unable to retrieve the contacts.", e);
		}
		NodeList contactsList = root.getElementsByTagName("entry");
		if (contactsList != null && contactsList.getLength() > 0) {
			for (int i = 0; i < contactsList.getLength(); i++) {
				Element contact = (Element) contactsList.item(i);
				String fname = "";
				NodeList l = contact.getElementsByTagNameNS(
						"http://schemas.google.com/g/2005", "email");
				String address = null;
				String emailArr[] = null;
				if (l != null && l.getLength() > 0) {
					Element el = (Element) l.item(0);
					if (el != null) {
						address = el.getAttribute("address");
					}
					if (l.getLength() > 1) {
						emailArr = new String[l.getLength() - 1];
						for (int k = 1; k < l.getLength(); k++) {
							Element e = (Element) l.item(k);
							if (e != null) {
								emailArr[k - 1] = e.getAttribute("address");
							}
						}
					}
				}
				String lname = "";
				String dispName = XMLParseUtil.getElementData(contact,
				"title");
				if (address != null && address.length() > 0) {
					Contact p = new Contact();
					p.setFirstName(fname);
					p.setLastName(lname);
					p.setEmail(address);
					p.setDisplayName(dispName);
					p.setOtherEmails(emailArr);
					plist.add(p);
				}
			}
		}
		return plist;
	}

	public Response fetchToken(final TokenExchange exchange,
			final UrlEncodedParameterMap params, final Endpoint endpoint,
			final Token token) throws IOException {
		// via GET, POST or Authorization
		Transport transport = endpoint.getTransport();

		// via HMAC-SHA1 or PLAINTEXT
		Signature sig = endpoint.getSignature();

		// nonce and timestamp generator
		NonceAndTimestamp nts = SimpleNonceAndTimestamp.getDefault();

		// http connector
		HttpConnector connector = SimpleHttpConnector.getDefault();

		// returns the http response
		return transport.send(params, endpoint, token, exchange, nts, sig,
				connector);
	}

	/**
	 * Logout
	 */
	public void logout() {
		token = null;
	}
}