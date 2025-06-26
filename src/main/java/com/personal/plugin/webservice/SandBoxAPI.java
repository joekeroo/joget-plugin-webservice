package com.personal.plugin.webservice;

import com.personal.plugin.util.Constants;
import com.personal.plugin.webservice.models.SandBoxData;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Calendar;
import java.util.Date;
import java.util.Map;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.sql.DataSource;
import org.apache.commons.codec.digest.DigestUtils;
import org.joget.apps.app.service.AppUtil;
import org.joget.commons.util.LogUtil;
import org.joget.plugin.base.DefaultApplicationPlugin;
import org.joget.plugin.base.PluginWebSupport;
import org.joget.workflow.model.service.WorkflowUserManager;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.simple.parser.ParseException;

public class SandBoxAPI
	extends DefaultApplicationPlugin
	implements PluginWebSupport {

	@Override
	public Object execute(Map props) {
		return null;
	}

	@Override
	public String getName() {
		return this.getClass().toString();
	}

	@Override
	public String getVersion() {
		return "1.0.0";
	}

	@Override
	public String getDescription() {
		return "API for SandBox";
	}

	@Override
	public String getLabel() {
		return "SandBox - API";
	}

	@Override
	public String getClassName() {
		return this.getClass().toString();
	}

	@Override
	public String getPropertyOptions() {
		return "";
	}

	private void msg(String msg) {
		LogUtil.info(this.getClass().getName(), msg);
	}

	@Override
	public void webService(
		HttpServletRequest request,
		HttpServletResponse response
	) throws ServletException, IOException {
		try {
			String method = request.getParameter("method");
			method = method == null ? "" : method;

			if (method.equals("")) {
				response.sendError(
					HttpServletResponse.SC_BAD_REQUEST,
					Constants.API.STATUS.ERROR_SPECIFYAPIMETHOD
				);
				return;
			}

			boolean specialRequest = false;
			boolean isAuthed = false;
			isAuthed = authenticateRequest(request);

			JSONObject payload = new JSONObject();
			String origin = request.getHeader("Origin");
			String referer = request.getHeader("Referer");
			String dateTime = request.getHeader("dateTime");
			String cur_dateTime = getCurrentDateTime("YYYYMMddHHmmss");

			origin = origin == null ? "" : origin;
			referer = referer == null ? "" : referer;
			dateTime = dateTime == null ? "" : dateTime;
			cur_dateTime = cur_dateTime == null ? "" : cur_dateTime;

			msg(
				String.format(
					"SandBox API: %s, Referer: %s, origin: %s, dateTime: %s, cur_dateTime: %s",
					method,
					referer,
					origin,
					dateTime,
					cur_dateTime
				)
			);

			switch (method) {
				case "getTestData":
					try {
						payload = new SandBoxData(request).getTestData();
					} catch (Exception e) {
						payload.put("Error", e.getMessage());
					}
					break;
				case "hashMe":
					payload = doHash(request);
					specialRequest = true;
					break;
				default:
					response.sendError(
						HttpServletResponse.SC_BAD_REQUEST,
						Constants.API.STATUS.ERROR_SPECIFYAPIMETHOD
					);
					return;
			}

			if (!isAuthed && !specialRequest) {
				response.sendError(
					HttpServletResponse.SC_FORBIDDEN,
					Constants.API.STATUS.ERROR_CREDENTIALS
				);
				return;
			}

			if (payload != null) {
				JSONObject jsonObj = new JSONObject();
				JSONArray jsonArr = new JSONArray();
				jsonArr.put(payload);

				jsonObj.put(
					"dateTime",
					getCurrentDateTime("YYYY-MM-dd hh:mm:ss")
				);

				if (payload.has("dataArray")) {
					jsonArr = payload.getJSONArray("dataArray");
				}

				jsonObj.put("size", jsonArr.length());
				jsonObj.put("data", jsonArr);
				jsonObj.write(response.getWriter());
			}
		} catch (JSONException ex) {
			ex.printStackTrace();
			response.sendError(
				HttpServletResponse.SC_FORBIDDEN,
				ex.getMessage()
			);
		} finally {
			return;
		}
	}

	private boolean authenticateRequest(HttpServletRequest request) {
		String hash = request.getHeader("hash");
		String apiKey = request.getHeader("apiKey");
		String dateTime = request.getHeader("dateTime");
		String username = request.getHeader("username");
		String cur_hash = Constants.API_SECRET;

		hash = hash == null ? "" : hash;
		apiKey = apiKey == null ? "" : apiKey;
		dateTime = dateTime == null ? "" : dateTime;
		username = username == null ? "" : username;

		if (hash.equals("") || apiKey.equals("") || dateTime.equals("")) {
			return false;
		}

		if (username.equals("")) {
			return false;
		} else if (!validateUsername(username)) {
			return false;
		}

		cur_hash = cur_hash + "|" + dateTime + "|" + apiKey + "|" + username;
		String hashCheck = DigestUtils.sha256Hex(cur_hash);

		DateTimeFormatter type = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
		LocalDateTime api_dateTime = LocalDateTime.parse(dateTime, type);
		LocalDateTime cur_dateTime = LocalDateTime.now();

		// Calculate the duration difference
		Duration duration = Duration.between(api_dateTime, cur_dateTime);
		long minutesDifference = Math.abs(duration.toMinutes());

		// Validate if hash is the same and api called within 10 minutes
		if (hash.equals(hashCheck) && minutesDifference <= 10) {
			return true;
		} else {
			return false;
		}
	}

	private JSONObject doHash(HttpServletRequest request) throws JSONException {
		String message = request.getHeader("message");
		message = message == null ? "" : message;
		String hashed = DigestUtils.sha256Hex(message);

		return new JSONObject().put("message", hashed);
	}

	private String getCurrentDateTime(String format) {
		Calendar calendar = Calendar.getInstance();
		SimpleDateFormat formatter = new SimpleDateFormat(format);
		return formatter.format(calendar.getTime());
	}

	private String getCurrentUsername() {
		WorkflowUserManager workflowUserManager =
			(WorkflowUserManager) AppUtil.getApplicationContext()
				.getBean("workflowUserManager");
		String username = workflowUserManager.getCurrentUsername();

		return username;
	}

	private boolean validateUsername(String username) {
		boolean exists = false;
		Connection con = null;

		try {
			DataSource ds = (DataSource) AppUtil.getApplicationContext()
				.getBean("setupDataSource");
			con = ds.getConnection();

			if (!con.isClosed()) {
				String sql = "select id from dir_user where id = ?";
				PreparedStatement stmt = con.prepareStatement(sql);
				stmt.setObject(1, username);
				ResultSet rs = stmt.executeQuery();

				//Get value from columns of record(s)
				while (rs.next()) {
					exists = true;
					break;
				}
			}
		} catch (Exception e) {
			LogUtil.error("validateUsername", e, "Error validating username");
		} finally {
			//always close the connection after used
			try {
				if (con != null) {
					con.close();
				}
			} catch (SQLException e) {
				/* ignored */
			}
		}

		return exists;
	}
}
