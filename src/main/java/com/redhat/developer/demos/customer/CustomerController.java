package com.redhat.developer.demos.customer;

import io.opentracing.Tracer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.Random;

@RestController
public class CustomerController {

    private static final String RESPONSE_STRING_FORMAT = "customer => %s\n";

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final RestTemplate restTemplate;

    private static final String[] names = {"Harry Potter", "Hermione Granger", "Lord Voldemort", "Draco Malfoy", "Ron Weasley",
            "Severus Snape", "Sirius Black", "Albus Dumbledore", "Rubeus Hagrid", "Ginny Weasley"};
    private static final String[] addresses = {"1800 Sunset Bvd, Los Angeles", "200 5h Ave, New York City", "1600 Pennsylvania Ave NW, Washington DC"};

    @Value("${preferences.api.url:http://preference:8080}")
    private String remoteURL;

    @Autowired
    private Tracer tracer;

    public CustomerController(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    // SB 1.5.X actuator does not allow subpaths on custom health checks URL/do in easy way
    @RequestMapping("/health/ready")
    @ResponseStatus(HttpStatus.OK)
    public void ready() {}

    // SB 1.5.X actuator does not allow subpaths on custom health checks URL/do in
    // easy way
    @RequestMapping("/health/live")
    @ResponseStatus(HttpStatus.OK)
    public void live() {}

    @RequestMapping(value = "/", method = RequestMethod.POST, consumes = "text/plain")
    public ResponseEntity<String> addRecommendation(@RequestBody String body) {
        try {
            return restTemplate.postForEntity(remoteURL, body, String.class);
        } catch (HttpStatusCodeException ex) {
            logger.warn("Exception trying to post to preference service.", ex);
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(String.format("%d %s", ex.getRawStatusCode(), createHttpErrorResponseString(ex)));
        } catch (RestClientException ex) {
            logger.warn("Exception trying to post to preference service.", ex);
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ex.getMessage());
        }
    }

    @RequestMapping(value = "/customer", method = RequestMethod.GET)
    public ResponseEntity<Customer> getCustomer(@RequestHeader("User-Agent") String userAgent, @RequestHeader(value = "user-preference", required = false) String userPreference) {
        try {
            /**
             * Set baggage
             */
            tracer.activeSpan().setBaggageItem("user-agent", userAgent);
            if (userPreference != null && !userPreference.isEmpty()) {
                tracer.activeSpan().setBaggageItem("user-preference", userPreference);
            }

            ResponseEntity<Preference> responseEntity = restTemplate.getForEntity(remoteURL, Preference.class);
            Preference preferenceResponse = responseEntity.getBody();
            Customer customer = new Customer();

            Random rand = new Random();
            Integer id = rand.nextInt(1000000);
            customer.setId(id);
            String name = names[id % 10];
            customer.setName(name);
            String address = addresses[id % 3];
            customer.setAddress(address);
            customer.setPreference(preferenceResponse);

            return ResponseEntity.ok(customer);
        } catch (HttpStatusCodeException ex) {
            logger.warn("Exception trying to get the response from preference service.", ex);
            return new ResponseEntity(HttpStatus.BAD_REQUEST);
        } catch (RestClientException ex) {
            logger.warn("Exception trying to get the response from preference service.", ex);
            return new ResponseEntity(HttpStatus.BAD_REQUEST);
        }
    }

    private String createHttpErrorResponseString(HttpStatusCodeException ex) {
        String responseBody = ex.getResponseBodyAsString().trim();
        if (responseBody.startsWith("null")) {
            return ex.getStatusCode().getReasonPhrase();
        }
        return responseBody;
    }

}
