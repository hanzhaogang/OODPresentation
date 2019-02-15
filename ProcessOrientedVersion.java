package com.amazonaws.samples;
/*
 * Copyright 2014-2017 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 * You may not use this file except in compliance with the License.
 * A copy of the License is located at
 *
 *  http://aws.amazon.com/apache2.0
 *
 * or in the "license" file accompanying this file. This file is distributed
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.commons.io.IOUtils;

import com.amazonaws.AmazonClientException;
import com.amazonaws.auth.profile.ProfileCredentialsProvider;
import com.amazonaws.regions.Region;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.simpleemail.AmazonSimpleEmailService;
import com.amazonaws.services.simpleemail.AmazonSimpleEmailServiceClientBuilder;
import com.amazonaws.services.simpleemail.model.Body;
import com.amazonaws.services.simpleemail.model.Content;
import com.amazonaws.services.simpleemail.model.Destination;
import com.amazonaws.services.simpleemail.model.Message;
import com.amazonaws.services.simpleemail.model.SendEmailRequest;

public class AmazonSESSample {

	static final String windows_prefix="d:/";
	static final String ubuntu_prefix="~/";
    static String allEmail_path="allEmail.txt";
	static String addedEmail_path="addedEmail.txt";
	static String frequency_path="frequency_heartBeat.txt";
    
    /*
     * Before running the code:
     *      Fill in your AWS access credentials in the provided credentials
     *      file template, and be sure to move the file to the default location
     *      (C:\\Users\\Administrator\\.aws\\credentials) where the sample code will load the
     *      credentials from.
     *      https://console.aws.amazon.com/iam/home?#security_credential
     *
     * WARNING:
     *      To avoid accidental leakage of your credentials, DO NOT keep
     *      the credentials file in your source directory.
     */

	
	/*
	 * todo 1 log
	 *      2 test logic
	 *      3 welcomenb page monitor
	 *      4 refactor&ood
	 */
    public static void main(String[] args) throws IOException {
    	// determine the correct file path.
    	String os=System.getProperty("os.name");
    	System.out.println(os);
    	if(os.contains("Window")) {
    		allEmail_path=windows_prefix+"allEmail.txt";
    		addedEmail_path=windows_prefix+"addedEmail.txt";
    		frequency_path=windows_prefix+"frequency_heartBeat.txt";
    	}else {
    		/*allEmail_path=ubuntu_prefix+"allEmail.txt";
    		addedEmail_path=ubuntu_prefix+"addedEmail.txt";
    		frequency_path=ubuntu_prefix+"frequency_heartBeat.txt";
    		*/
    	}
    	
   		String pageContent="empty";
   		String prePageContent="empty";
		URL url = new URL("https://www.welcomenb.ca/content/wel-bien/en/immigrating/content/HowToImmigrate/NBProvincialNomineeProgram.html");
		final int DAY=1000*60*60*24;// 24 hours
		long runningTime=0;

		while(true) {
			System.out.println("entering the main loop!\n\n");
			sendAccountSetupMail(allEmail_path,addedEmail_path);

    		try {
    			pageContent=getWebPageContent(url);

    			if(prePageContent.equals("empty")) {
    				prePageContent=new String(pageContent);
    			}else {
    				if(!prePageContent.equals(pageContent)&&!prePageContent.equals("empty")) {// web page changed
    					System.out.println("web page content changed!");
    					boolean sent=sendPageChangedNotification(pageContent);
    					if(sent) {
    						System.out.println("page changed notification mail sent");
    					}
    					prePageContent=new String(pageContent);
    				}
    			}
    			
    			int checkingInterval=1000*60*60;//60 minutes
    			Thread.sleep(checkingInterval);
    			runningTime+=checkingInterval;

    			//send account setup mail
    			if(runningTime%DAY==0) {
    				sendAccountSetupMail(allEmail_path,addedEmail_path);
    			}

    			//send heart beat mail
    			System.out.println("server heart beat ");
    			if(runningTime%DAY==0) {
    				List<String> allMail=getEmailFrom(allEmail_path);
    				List<Integer> freq=getFrequencyFrom(frequency_path);
    				for(int i=0;i<allMail.size();i++) {
    					Integer fre=freq.get(i);
    					if((runningTime/DAY)%fre==0) {
    						boolean sent=sendHeartBeatMail(pageContent,fre);
    						if(sent) {
    							System.out.println("heart beat mail sent");
    						}
    					}
    					
    				}
    				
    			}
    		} catch (IOException e1) {
    			// TODO Auto-generated catch block
    			e1.printStackTrace();
    		} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
    	}
        
    }

	private static void sendAccountSetupMail(String allEmail_path,String addedEmail_path) {
		List<String> allAddress=getEmailFrom(allEmail_path);
		List<String> addedAddress=getEmailFrom(addedEmail_path);
		
		//send the first mail to new account
		List<String> toAddress=new ArrayList<>();
		for(String address:allAddress) {
			if(!addedAddress.contains(address)) {
				toAddress.add(address);
			}
		}
		boolean sent=sendAccountSetupMail(toAddress);
		if(sent) {
			recordAddedAccount(toAddress,addedEmail_path);
			System.out.println("account setup notification mail sent, please check!");
		}
	}

	private static void recordAddedAccount(List<String> toAddress,String addedEmail_path) {
		PrintWriter out = null;
		try {
		  out= new PrintWriter(new FileOutputStream(
				    new File(addedEmail_path), 
				    true /* append = true */)); 
		  for(String addr:toAddress) {
			  out.println(addr);
		  }
		} catch (FileNotFoundException e) {
		  e.printStackTrace();
		} finally {
		  if(out != null) {
		    out.flush();
		    out.close();
		  }
		}
	}
  
    private static String getWebPageContent(URL url) throws IOException {
		URLConnection con = url.openConnection();
		InputStream in = con.getInputStream();
		String encoding = con.getContentEncoding();
		encoding = encoding == null ? "UTF-8" : encoding;
		String pageContent = IOUtils.toString(in, encoding);
		return pageContent;
    }

    private static boolean sendAccountSetupMail(List<String> toAddress) {
    	
    	
    	String FROM="hanzhaogang@gmail.com";
    	String BODY="Hi: \n you are receiving this mail because you are regeistered to the nb ee web page notification service."
				+ "you will be notified whenever the web page is changed, and you will receive the web page"
				+ "content once a day, even it is not changed, indicating that the function is working for you. "
				+ "If you don't want to receive the daily reporting email, contact me by hanzhaogang@gmail.com";
    	String SUBJECT="Confirm to the nb web page monitor service";
        return sendAll(toAddress,FROM,BODY,SUBJECT);
    }
    
    private static List<String> getEmailFrom(String path){
    	return getLineFrom(path);
    }
    
    private static List<Integer> getFrequencyFrom(String path){ 
    	List<String> line=getLineFrom(path);
    	List<Integer> freq=line.stream().map(Integer::parseInt).collect(Collectors.toList());
    	return freq;
    }
    
    private static List<String> getLineFrom(String path){
    	List<String> line=new ArrayList<String>();
    	try (BufferedReader r = 
    			Files.newBufferedReader(Paths.get(path), StandardCharsets.UTF_8)){
    				r.lines().forEach(ln->line.add(ln));
    	}catch(Exception e) {
    		System.out.println(e);
    	}
    	return line;
    }
    
    
    private static boolean pageChanged() {
    	return false;
    }
   
    private static boolean sendHeartBeatMail(String pageContent,Integer days) {
    	String FROM = "hanzhaogang@gmail.com";  
        // production access, this address must be verified.
        String BODY = "This email was sent to notify that we are monitoring the website for you. "
        		+ "the newest content as below:\n"
        		+ "(you will receive this heart beat mail every"+days+"days. If you want to change"
        				+ "the frequency, contact the administrator.)"
        		+"\n\n\n\n"+ pageContent;

        String SUBJECT = "we are monitoring the website for you, and nothing has changed!";
    	
        List<String> allEmail=getEmailFrom(allEmail_path);
        return sendAll(allEmail,FROM,BODY,SUBJECT);
    }

    private static boolean sendPageChangedNotification(String pageContent) {
        // Replace with your "From" address. This address must be verified.
    	String FROM = "hanzhaogang@gmail.com";  
        // production access, this address must be verified.
        String BODY = "This email was sent to notify that something new updated on the website, the newest content as below:"+
        pageContent;

        String SUBJECT = "something new updated on the website we are monitoring!!!";
    	
        List<String> allEmail=getEmailFrom(allEmail_path);
        return sendAll(allEmail,FROM,BODY,SUBJECT);
    }
    
   
    private static boolean sendAll(List<String> emailList,String FROM,String BODY,String SUBJECT) {
    	boolean res=false;
        for(String email:emailList) {
        	boolean sent=sendMail(FROM,email,BODY,SUBJECT);
        	if(sent)
        		res=true;
        }
        return res;
    }
    private static boolean sendMail(String FROM,String TO,String BODY,String SUBJECT) {
    	// Construct an object to contain the recipient address.
        Destination destination = new Destination().withToAddresses(new String[]{TO});

        // Create the subject and body of the message.
        Content subject = new Content().withData(SUBJECT);
        Content textBody = new Content().withData(BODY);
        Body body = new Body().withText(textBody);

        // Create a message with the specified subject and body.
        Message message = new Message().withSubject(subject).withBody(body);

        // Assemble the email.
        SendEmailRequest request = new SendEmailRequest().withSource(FROM).
        		withDestination(destination).withMessage(message);

        try {
            System.out.println(
            		"Attempting to send an email through Amazon SES by using the AWS SDK for Java...");

            /*
             * The ProfileCredentialsProvider will return your [default]
             * credential profile by reading from the credentials file located at
             * (C:\\Users\\Administrator\\.aws\\credentials).
             *
             * TransferManager manages a pool of threads, so we create a
             * single instance and share it throughout our application.
             */
            ProfileCredentialsProvider credentialsProvider = new ProfileCredentialsProvider();
            try {
                credentialsProvider.getCredentials();
            } catch (Exception e) {
                throw new AmazonClientException(
                        "Cannot load the credentials from the credential profiles file. " +
                        "Please make sure that your credentials file is at the correct " +
                        "location (C:\\Users\\Administrator\\.aws\\credentials), and is in valid format.",
                        e);
            }

            // Instantiate an Amazon SES client, which will make the service call with the supplied AWS credentials.
            AmazonSimpleEmailService client = AmazonSimpleEmailServiceClientBuilder.standard()
                .withCredentials(credentialsProvider)
                // Choose the AWS region of the Amazon SES endpoint you want to connect to. Note that your production
                // access status, sending limits, and Amazon SES identity-related settings are specific to a given
                // AWS region, so be sure to select an AWS region in which you set up Amazon SES. Here, we are using
                // the US East (N. Virginia) region. Examples of other regions that Amazon SES supports are US_WEST_2
                // and EU_WEST_1. For a complete list, see http://docs.aws.amazon.com/ses/latest/DeveloperGuide/regions.html
                .withRegion("us-west-2")
                .build();

            // Send the email.
            client.sendEmail(request);
            System.out.println("Email sent!");
            return true;

        } catch (Exception ex) {
            System.out.println("The email was not sent.");
            System.out.println("Error message: " + ex.getMessage());
            return false;
        }
    }
}
