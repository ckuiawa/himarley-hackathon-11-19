package com.himarley.hackathon;

import java.util.Date;
import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.himarley.aws.ApiGatewayResponse;
import com.himarley.model.Identity;
import com.himarley.model.MarleyPayload;
import com.himarley.model.Message;
import com.himarley.util.LambdaUtil;
import com.himarley.util.ServiceUtil;
import com.joestelmach.natty.DateGroup;
import com.joestelmach.natty.Parser;

import biweekly.Biweekly;
import biweekly.ICalendar;
import biweekly.component.VEvent;
import biweekly.property.Attendee;
import biweekly.property.Organizer;
import biweekly.property.Summary;
import biweekly.util.Duration;

public class Handler implements RequestHandler<Map<String, Object>, ApiGatewayResponse> {

	private static final Logger LOG = LogManager.getLogger(Handler.class);

	private static final Parser parser = new Parser();

	@Override
	public ApiGatewayResponse handleRequest(Map<String, Object> input, Context context) {

		ObjectMapper objMapper = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

		MarleyPayload payload = null;

		try {
			if (input != null && input.containsKey("body")) {
				String body = input.get("body").toString();
				payload = objMapper.readValue(body, MarleyPayload.class);

				/*
				 * 1) parse NLP message
				 * 2) build ICS
				 * 3) Store ICS on S3
				 * 4) Add Event to payload with link
				 * 5) Update connect UI to display link
				 *
				 */

				Message request = payload.getRequest();
				if (request != null) {
					String incomingText = request.getText();

					if (incomingText != null) {
						Date scheduleDate = parseForDate(incomingText);

						if (scheduleDate != null) {
							String icsFile = createIcsFile(payload, scheduleDate);
						}

					}

				}



				// Tells router we have responded.
//				ServiceUtil.serviceResponse(payload);

				return LambdaUtil.getInstance().getSuccessfulResponse(payload);

			}
			else {
				System.out.println("Input did not contain a body.");
				return LambdaUtil.getInstance().getErrorResponse("Body not included in input.", 500);
			}


		}
		catch (Exception ex) {
			ex.printStackTrace();
			return LambdaUtil.getInstance().getErrorResponse("Exception occurred processing request: " + ex.getMessage(), 500);

		}
	}

	protected Date parseForDate(String incoming) {
		if (incoming == null) {
			return null;
		}

		List<DateGroup> results = parser.parse(incoming);
		if (results.isEmpty()) {
			return null;
		}

		for (Date date: results.get(0).getDates()) {
			if (date != null) {
				System.out.println("Found a date: " + date.toString());
				return date;
			}
		}
		return null;
	}

	protected String createIcsFile(MarleyPayload payload, Date scheduleDate) {
		ICalendar ical = new ICalendar();
		VEvent event = new VEvent();
		Summary summary = event.setSummary("Claim discussion");
		summary.setLanguage("en-us");

		Identity identity = payload.getIdentity();
		if (identity != null && identity.getFirst() != null && identity.getLast() != null) {
			String name = identity.getFirst() + " " + identity.getLast();

			Attendee attendee = new Attendee(name, identity.getEmail(), identity.getMobile());
			event.addAttendee(attendee);
		}

		com.himarley.model.Context context = payload.getContext();
		if (context != null) {
			Identity operator = context.getOperator();
			if (operator != null) {
				String name = operator.getFirst() + " " + operator.getLast();
				Organizer org = new Organizer(name, operator.getEmail());
				event.setOrganizer(org);
			}
		}

		// TODO: Add a description or some other field that
		// helps both operator and insured (include phone number to call, etc)...


		event.setDateStart(scheduleDate);

		Duration duration = new Duration.Builder().hours(1).build();
		event.setDuration(duration);

		ical.addEvent(event);

		return Biweekly.write(ical).go();
	}


}
