package com.himarley.hackathon;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import java.util.Calendar;
import java.util.Date;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.himarley.model.Identity;
import com.himarley.model.MarleyPayload;
import com.himarley.model.Message;


public class HandlerTest {

	private static final ObjectMapper mapper = new ObjectMapper();

	@Before
	public void setUp() throws Exception {
	}

	private Handler createTestSubject() {
		return new Handler();
	}

//	@Test
//	public void testHandleRequestNoPayload() throws Exception {
//		Handler handler;
//		Map<String, Object> input = null;
//		Context context = null;
//		ApiGatewayResponse result;
//
//		handler = createTestSubject();
//		result = handler.handleRequest(input, context);
//
//		assertEquals(500, result.getStatusCode());
//
//	}

//	@Test
//	public void testHandleRequesValidPayload() throws Exception {
//		Handler testSubject;
//		Map<String, Object> input = null;
//		Context context = null;
//		ApiGatewayResponse result;
//
//
//		testSubject = createTestSubject();
//
//		// TODO: Add payload
//		result = testSubject.handleRequest(input, context);
//
//		assertEquals(500, result.getStatusCode());
//
//	}
//
	@Test
	@Ignore
	public void testParseForDateNull() {
		Handler handler = createTestSubject();
		Date result = handler.parseForDate(null);

		assertNull(result);
	}


	@Test
	@Ignore
	public void testParseForDateNoDateInInput() {
		Handler handler = createTestSubject();
		Date result = handler.parseForDate("Hi there, Marley.  How are you?");

		assertNull(result);
	}

	@Test
	@Ignore
	public void testParseForDateTodayAtMidnight() {
		Handler handler = createTestSubject();
		String input = "Today at noon.";
		Date result = handler.parseForDate(input);

		Date expected = getNoonToday();

		assertNotNull(result);
		assertEquals(expected, result);

	}


	@Test
	@Ignore
	public void testcreateIscFile() throws Exception {
		MarleyPayload payload = createTestPayload();

		Handler handler = createTestSubject();

		Date schedule = getNoonToday();

		String icsResponse = handler.createIcsFile(payload, schedule);

		String expected = "BEGIN:VCALENDAR\n" +
				"VERSION:2.0\n" +
				"PRODID:-//Michael Angstadt//biweekly 0.6.2//EN\n" +
				"BEGIN:VEVENT\n" +
				"UID:<removed>\n" +
				"DTSTAMP:<removed>\n" +
				"SUMMARY;LANGUAGE=en-us:Claim discussion\n" +
				"ATTENDEE;CN=Marley Insured;EMAIL=hack@himarley.com:+15552070001\n" +
				"DTSTART:20181119T170000Z\n" +
				"DURATION:PT1H\n" +
				"END:VEVENT\n" +
				"END:VCALENDAR\n";

		String actual = scrubIcs(icsResponse);
		//assertEquals(expected, actual);

	}


//	@Test
//	@Ignore
//	public void testS3Upload() throws Exception {
//		MarleyPayload payload = createTestPayload();
//
//		Handler handler = createTestSubject();
//
//		Date schedule = getNoonToday();
//
//		String icsResponse = handler.createIcsFile(payload, schedule);
//
//		handler.uploadToS3(icsResponse, payload);
//	}



	private String scrubIcs(String icsResponse) {

		String firstReplace = icsResponse.replaceFirst("UID:.*", "UID:<removed>");
		String secondReplace = firstReplace.replaceFirst("DTSTAMP:.*", "DTSTAMP:<removed>");
		return secondReplace;
	}

	private Date getNoonToday() {
		Calendar cal = Calendar.getInstance();

		// Java Calendar is stupid.
		cal.set(Calendar.HOUR, 0);
		cal.set(Calendar.MILLISECOND, 0);
		cal.set(Calendar.SECOND, 0);
		cal.set(Calendar.MINUTE, 0);
		cal.set(Calendar.HOUR, 12);
		cal.add(Calendar.DAY_OF_MONTH, -1);
		cal.set(Calendar.HOUR, 12);

		return  cal.getTime();

	}

	private MarleyPayload createTestPayload() {
		MarleyPayload payload = new MarleyPayload();

		// identity
		Identity insured = new Identity();
		insured.setFirst("Marley");
		insured.setLast("Insured");
		insured.setEmail("unittest@himarley.com");
		insured.setMobile("+15552070001");
		payload.setIdentity(insured);

		// context
		// context.operator
		Identity operator = new Identity();
		operator.setFirst("Marley");
		operator.setLast("Operator");
		operator.setEmail("operator@himarley.com");
		operator.setMobile("+15552070001");
		operator.setContactNumber("+15555555555");
		com.himarley.model.Context context = new com.himarley.model.Context();
		context.setOperator(operator);
		payload.setContext(context);

		// Request
		// Request.text
		Message request = new Message();
		request.setText("Let's meet today at noon.");
		payload.setRequest(request);


		return payload;
	}

	private MarleyPayload createTestPayload(String text) {
		MarleyPayload payload = createTestPayload();
		Message request = new Message();
		request.setText(text);
		payload.setRequest(request);
		return payload;

	}

//	@Test
//	public void testFullProcess()
//	{
//		fullProcess("can we meet tomorrow at 3 pm EST?");
//	}
//
//	private String fullProcess(String text) {
//		MarleyPayload payload = createTestPayload(text);
//		Handler handler = createTestSubject();
//
//		String url = handler.processPayload(payload);
//
//		if (url != null)
//			return url;
//
//		return null;
//	}
}