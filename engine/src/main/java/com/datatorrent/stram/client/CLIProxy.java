/*
 *  Copyright (c) 2012-2013 Malhar, Inc.
 *  All Rights Reserved.
 */
package com.datatorrent.stram.client;

import java.io.*;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang.StringUtils;
import org.codehaus.jettison.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author David Yan <david@datatorrent.com>
 */
public class CLIProxy
{
  private static final Logger LOG = LoggerFactory.getLogger(CLIProxy.class);

  public static class CommandException extends Exception
  {
    public CommandException(String message)
    {
      super(message);
    }

  }

  private static class StreamGobbler extends Thread
  {
    InputStream is;
    StringBuilder content = new StringBuilder();

    StreamGobbler(InputStream is)
    {
      this.is = is;
    }

    String getContent()
    {
      return content.toString();
    }

    @Override
    public void run()
    {
      try {
        InputStreamReader isr = new InputStreamReader(is);
        BufferedReader br = new BufferedReader(isr);
        String line;
        while ((line = br.readLine()) != null) {
          if (!line.contains(" DEBUG ")) {
            content.append(line);
            content.append("\n");
          }
        }
      }
      catch (IOException ex) {
        LOG.error("Caught exception", ex);
      }
    }

  }

  public static JSONObject getLogicalPlan(String jarUri, String appName, List<String> libjars) throws Exception
  {
    StringBuilder sb = new StringBuilder("show-logical-plan ");
    if (!libjars.isEmpty()) {
      sb.append("-libjars ");
      sb.append(StringUtils.join(libjars, ','));
      sb.append(" ");
    }
    sb.append(jarUri);
    sb.append(" \"");
    sb.append(appName);
    sb.append("\"");
    return issueCommand(sb.toString());
  }

  public static JSONObject launchApp(String jarUri, String appName, Map<String, String> properties, List<String> libjars) throws Exception
  {
    StringBuilder sb = new StringBuilder("launch ");
    for (Map.Entry<String, String> entry : properties.entrySet()) {
      sb.append("-D ");
      sb.append(entry.getKey());
      sb.append("=\"");
      sb.append(entry.getValue());
      sb.append("\" ");
    }
    if (!libjars.isEmpty()) {
      sb.append("-libjars ");
      sb.append(StringUtils.join(libjars, ','));
      sb.append(" ");
    }
    sb.append(jarUri);
    sb.append(" \"");
    sb.append(appName);
    sb.append("\"");
    return issueCommand(sb.toString());
  }

  public static JSONObject getApplications(String jarUri) throws Exception
  {
    return issueCommand("show-logical-plan \"" + jarUri + "\"");
  }

  private static JSONObject issueCommand(String command) throws Exception
  {
    String shellCommand = "dtcli -r -e '" + command + "'";
    Process p = Runtime.getRuntime().exec(new String[] {"bash", "-c", shellCommand});
    StreamGobbler errorGobbler = new StreamGobbler(p.getErrorStream());
    StreamGobbler outputGobbler = new StreamGobbler(p.getInputStream());
    errorGobbler.start();
    outputGobbler.start();
    int exitValue = p.waitFor();
    LOG.debug("Executed: {} ; exit code: {}", shellCommand, exitValue);
    LOG.debug("Output: {}", outputGobbler.getContent());
    LOG.debug("Error: {}", errorGobbler.getContent());
    if (exitValue == 0) {
      return new JSONObject(outputGobbler.getContent());
    }
    else {
      throw new CommandException(errorGobbler.getContent());
    }
  }

}
