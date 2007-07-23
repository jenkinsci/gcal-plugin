/*
  Copyright 2007 Arnaud Lacour
  
   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
 */
package gcal;

import hudson.Launcher;
import hudson.model.*;
import hudson.tasks.Publisher;
import hudson.tasks.Mailer;
import org.kohsuke.stapler.StaplerRequest;

import com.google.gdata.client.calendar.*;
import com.google.gdata.data.*;
import com.google.gdata.data.calendar.*;
import com.google.gdata.data.extensions.*;
import com.google.gdata.util.*;
import java.net.URL;
import java.net.MalformedURLException;
import java.io.IOException;


/**
 *  This is a really simple publisher for google calendar
 * @author Arnaud Lacour
 */
public class gcalPublisher extends Publisher {

  String serviceURL = "http://www.google.com/calendar/";

  private final String calendarID;


  private final String url;
  public String getUrl() {
    return url;
  }

  private final String login;
  public String getLogin(){
    return login;
  }

  private final String password;
  public String getPassword(){
    return password;
  }

  private final String statusToPublish;
  public String getStatusToPublish(){
    return statusToPublish;
  }

  gcalPublisher(String url, String login, String password, String statusToPublish) {
    int startPoint       = url.indexOf("/feeds/")+7;
    this.calendarID      = url.substring(startPoint,url.indexOf("/",startPoint+1));
    this.url             = serviceURL+"feeds/"+calendarID+"/private/full";
    this.login           = login;
    this.password        = password;
    this.statusToPublish = statusToPublish==null?"All":statusToPublish;
  }

  public boolean perform(Build build, Launcher launcher, BuildListener listener) {
    /*
    listener.getLogger().println("GCal: build duration    : "+build.getDurationString());
    listener.getLogger().println("GCal: build number      : "+build.getNumber());
    listener.getLogger().println("GCal: build timestamp 1 : "+build.getTimestampString() );
    listener.getLogger().println("GCal: build timestamp 2 : "+build.getTimestampString2() );
    listener.getLogger().println("GCal: build url         : "+build.getUrl() );
    listener.getLogger().println("GCal: build status url  : "+build.getBuildStatusUrl() );
    listener.getLogger().println("GCal: build display name: "+build.getDisplayName() );
    listener.getLogger().println("GCal: job display name  : "+build.getParent().getDisplayName());
    listener.getLogger().println("GCal: mailer base url   : "+Mailer.DESCRIPTOR.getUrl());
    */
    if ( statusToPublish.equals("All") ||
        (statusToPublish.equals("Successes") && build.getResult()==Result.SUCCESS ) ||
        (statusToPublish.equals("Failures") && build.getResult()==Result.FAILURE )
        ) {
      try {

        listener.getLogger().println("GCal: Preparing calendar update request...");
        CalendarService myService = new CalendarService("hudson.plugins.gcal");
        myService.setUserCredentials(login, password);
        URL postUrl = new URL(url);
        CalendarEventEntry myEntry = new CalendarEventEntry();

        myEntry.setTitle(new PlainTextConstruct(build.getParent().getDisplayName()+" build "+build.getDisplayName()+" "+(build.getResult()==Result.FAILURE?"failed":"succeeded")));
        myEntry.setContent(new PlainTextConstruct("Check the status for build "+build.getDisplayName()+" here "+ Mailer.DESCRIPTOR.getUrl() + build.getUrl()));
        DateTime startTime = DateTime.parseDateTime(build.getTimestampString2());
        DateTime endTime = DateTime.now();
        When eventTimes = new When();
        eventTimes.setStartTime(startTime);
        eventTimes.setEndTime(endTime);
        myEntry.addTime(eventTimes);

        // Send the request and receive the response:
        listener.getLogger().println("GCal: Sending calendar update request...");
        myService.insert(postUrl, myEntry);
        listener.getLogger().println("GCal: Calendar updated.");
      } catch (AuthenticationException e) {
        e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        listener.error("GCal: Could not authenticate user ["+login+"] to calendar ["+url+"]");
        build.setResult( Result.FAILURE );
      } catch (MalformedURLException e) {
        listener.error("GCal: The provided calendar URL ["+url+"] is not valid");
        e.printStackTrace();
      } catch (IOException e) {
        e.printStackTrace();
      } catch (ServiceException e) {
        listener.error("GCal: Google Calendar service exception");
        e.printStackTrace();
      }
    } else {
      listener.getLogger().println("GCal: Not publishing to calendar since this job is configured to publish "+statusToPublish.toLowerCase()+" and this build "+(build.getResult()==Result.SUCCESS?"succeeded":"failed"));
    }
    return true;
  }

  public Descriptor<Publisher> getDescriptor() {
    return DESCRIPTOR;
  }

  public static final DescriptorImpl DESCRIPTOR = new DescriptorImpl();
  public static final class DescriptorImpl extends Descriptor<Publisher> {
    DescriptorImpl() {
      super(gcalPublisher.class);
    }

    public String getDisplayName() {
      return "Publish job status to Google Calendar";
    }
    public String getHelpFile() {
      return "/plugin/gcal/help-projectConfig.html";
    }
    public gcalPublisher newInstance(StaplerRequest req) throws FormException {
      return new gcalPublisher(req.getParameter("gcal.url"),req.getParameter("gcal.login"),req.getParameter("gcal.password"),req.getParameter("gcal.statusToPublish"));
    }
  }
}