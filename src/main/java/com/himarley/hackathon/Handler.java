package com.himarley.hackathon;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Date;
import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.SdkClientException;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.CannedAccessControlList;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.PutObjectResult;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.himarley.aws.ApiGatewayResponse;
import com.himarley.model.Identity;
import com.himarley.model.MarleyPayload;
import com.himarley.model.Message;
import com.himarley.model.MessageType;
import com.himarley.util.LambdaUtil;
import com.himarley.util.ServiceUtil;
import com.joestelmach.natty.DateGroup;
import com.joestelmach.natty.Parser;

import biweekly.Biweekly;
import biweekly.ICalendar;
import biweekly.component.VEvent;
import biweekly.property.*;
import biweekly.util.Duration;

public class Handler implements RequestHandler<Map<String, Object>, ApiGatewayResponse> {

	private static final Logger log = LogManager.getLogger(Handler.class);

	private static final Parser parser = new Parser();

	private static final String bucketName = System.getProperty("bucket_name");


	@Override
	public ApiGatewayResponse handleRequest(Map<String, Object> input, Context context) {

		ObjectMapper objMapper = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

		MarleyPayload payload = null;

		try {
			log.info("Entering lambda.  Input: " + objMapper.writeValueAsString(input));
		} catch (JsonProcessingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

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

				String url = processPayload(payload);

				// Add event to the payload
				if(url != null) {
					log.info("Received S3 bucket URL: " + url);
					payload.getEvents().add(new Message("Calendar Event: " + url, MessageType.event));
				}

				// Tells router we have responded.
				ServiceUtil.serviceResponse(payload);

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

	protected String processPayload(MarleyPayload payload)
	{
		Message request = payload.getRequest();
		if (request != null) {
			String incomingText = request.getText();

			if (incomingText != null) {
				Date scheduleDate = parseForDate(incomingText);

				if (scheduleDate != null) {
					String icsFile = createIcsFile(payload, scheduleDate);
					if (icsFile != null) {
						String url = uploadToS3(icsFile, payload);
						if(url != null)
							return url;
					}
				}
			}
		}
		return null;
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
				// TODO: Currently just using the first date found in the text, could do something with sets down the road
				System.out.println("Found a date: " + date.toString());
				return date;
			}
		}
		return null;
	}

	protected String createIcsFile(MarleyPayload payload, Date scheduleDate) {
		ICalendar ical = new ICalendar();
		VEvent event = new VEvent();
		String summaryText = "Claim discussion";
		Summary summary = event.setSummary(summaryText);
		summary.setLanguage("en-us");

		String descDetails = summaryText;
		String descSummary = "";

		Identity identity = payload.getIdentity();
		if (identity != null && identity.getFirst() != null && identity.getLast() != null) {
			String name = identity.getFirst() + " " + identity.getLast();

			Attendee attendee = new Attendee(name, identity.getEmail(), identity.getMobile());
			attendee.setRsvp(true);
			event.addAttendee(attendee);

			descDetails += System.lineSeparator() + "Insured: " + name +
					System.lineSeparator() + identity.getEmail() +
					System.lineSeparator() + identity.getMobile() + System.lineSeparator();

			descSummary = " will call " + name + " at " + identity.getMobile() + " at " + scheduleDate.toString();
		}

		com.himarley.model.Context context = payload.getContext();
		if (context != null) {
			Identity operator = context.getOperator();
			if (operator != null) {
				String name = operator.getFirst() + " " + operator.getLast();
				Organizer org = new Organizer(name, operator.getEmail());
				event.setOrganizer(org);

				descDetails += System.lineSeparator() + "Operator: " + name +
						System.lineSeparator() + operator.getEmail() +
						System.lineSeparator() + operator.getMobile() + System.lineSeparator();

				descSummary = name + descSummary;
			}
		}

		// TODO: Add a description or some other field that
		// helps both operator and insured (include phone number to call, etc)...
		descDetails = descSummary + System.lineSeparator() + System.lineSeparator() + descDetails;
		Description desc = event.setDescription(descDetails);
		desc.setLanguage("en-us");

		event.setDateStart(scheduleDate);

		Duration duration = new Duration.Builder().hours(1).build();
		event.setDuration(duration);

		ical.addEvent(event);

		String icsContents = Biweekly.write(ical).go();

		log.info("Created an ICS file: " + icsContents);
		return icsContents;
	}


	protected String uploadToS3(String icsFile, MarleyPayload payload) {

		String email = getIdentityEmail(payload);

        String clientRegion = "us-east-1";
        String stringObjKeyName = email + new Date().getTime() + ".ics";

        try {

        	AmazonS3 s3Client = AmazonS3ClientBuilder.standard()
            		.withRegion(clientRegion)
                    .build();

        	// create calendar type metadata so that links are downloaded when clicked from s3 (vs opened as text)
            ObjectMetadata md = new ObjectMetadata();
            md.setContentType("text/calendar");

            // create input stream based on the icsFile content
            InputStream is = new ByteArrayInputStream( icsFile.getBytes() );

            // upload ics file with metadata included
        	s3Client.putObject(bucketName, stringObjKeyName, is, md);
        	s3Client.setObjectAcl(bucketName, stringObjKeyName, CannedAccessControlList.PublicRead);

        	// get and return the s3 URL
            String url = ((AmazonS3Client)s3Client).getResourceUrl(bucketName, stringObjKeyName);
            return url;
        }
        catch(AmazonServiceException e) {
            // The call was transmitted successfully, but Amazon S3 couldn't process
            // it, so it returned an error response.
            e.printStackTrace();
        }
        catch(SdkClientException e) {
            // Amazon S3 couldn't be contacted for a response, or the client
            // couldn't parse the response from Amazon S3.
            e.printStackTrace();
        }

        return null;
	}

	private String getIdentityEmail(MarleyPayload payload) {
		if (payload != null) {
			Identity identity = payload.getIdentity();
			if (identity != null && identity.getEmail() != null) {
				return identity.getEmail();
			}
		}
		return null;
	}


}
