package com.personal.plugin.webservice.models;

import java.lang.reflect.Field;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import javax.servlet.http.HttpServletRequest;
import javax.sql.DataSource;
import org.joget.apps.app.service.AppUtil;
import org.joget.commons.util.LogUtil;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class SandBoxData {

	HttpServletRequest request;

	private void msg(String msg) {
		LogUtil.info(this.getClass().getName(), msg);
	}

	class SandBoxAPIException extends Exception {

		public SandBoxAPIException(String message) {
			super(message);
		}
	}

	public SandBoxData(HttpServletRequest req) throws SQLException {
		request = req;
	}

	public JSONObject getTestData()
		throws JSONException, NumberFormatException, SandBoxAPIException {
		JSONArray array = new JSONArray();
		ArrayList<String> values = new ArrayList();
		HashMap<String, String> jsonKeyVal = new HashMap();

		String size = request.getParameter("size");
		String page = request.getParameter("page");
		String createdBy = request.getParameter("createdBy");
		String query = "";

		size = size == null ? "" : size;
		page = page == null ? "" : page;
		createdBy = createdBy == null ? "" : createdBy;
		query = "select * from app_fd_test";

		jsonKeyVal.put("id", "id");
		jsonKeyVal.put("name", "c_name");
		jsonKeyVal.put("email", "c_email");
		jsonKeyVal.put("device", "c_device");
		jsonKeyVal.put("status", "c_status");
		jsonKeyVal.put("createdBy", "createdBy");
		jsonKeyVal.put("dateCreated", "dateCreated");
		jsonKeyVal.put("dateModified", "dateModified");

		if (request.getParameter("createdBy") != null) {
			query += !query.contains("where") ? " where " : " and ";
			query += " createdBy = ? ";

			values.add(createdBy);
		}

		if (!query.contains("where")) {
			JSONObject empty = getArrangedJson();
			for (String key : jsonKeyVal.keySet()) {
				empty.put(key, "");
			}
			array.put(empty);

			return new JSONObject().put("dataArray", array);
		}

		query += " order by dateCreated desc ";
		query = addPagination(query, size, page);
		array = fetchData(query, values, jsonKeyVal);

		return new JSONObject().put("dataArray", array);
	}

	private String addPagination(String query, String size, String page)
		throws SandBoxAPIException {
		int defaultPage = 1;
		int defaultSize = 10;
		String pageQuery = "";
		boolean isPaging = false;

		if (!size.isEmpty()) {
			isPaging = true;
			try {
				defaultSize = Integer.parseInt(size);
				size = Integer.toString(defaultSize);
			} catch (Exception e) {
				throw new SandBoxAPIException(
					"Size must be a number " + e.getMessage()
				);
			}
		}

		if (!page.isEmpty()) {
			isPaging = true;
			try {
				defaultPage = Integer.parseInt(page);
				defaultPage = (defaultPage - 1) * defaultSize;
				page = Integer.toString(defaultPage);
				size = Integer.toString(defaultSize);
			} catch (Exception e) {
				throw new SandBoxAPIException(
					"Page must be a number " + e.getMessage()
				);
			}
		}

		if (isPaging) {
			if (!size.isEmpty()) {
				pageQuery = " limit " + size + " ";
			}
			if (!page.isEmpty()) {
				pageQuery += " offset " + page + " ";
			}
		}

		return query + pageQuery;
	}

	private JSONArray fetchData(
		String query,
		ArrayList<String> values,
		HashMap<String, String> jsonKeyVal
	) {
		JSONArray array = new JSONArray();
		Connection con = null;

		try {
			DataSource ds = (DataSource) AppUtil.getApplicationContext()
				.getBean("setupDataSource");
			con = ds.getConnection();

			if (!con.isClosed()) {
				PreparedStatement stmt = con.prepareStatement(query);

				int i = 0;
				for (String value : values) {
					stmt.setObject(++i, value);
				}

				int count = 0;
				ResultSet rs = stmt.executeQuery();

				while (rs.next()) {
					JSONObject jsonObj = getArrangedJson();
					for (String key : jsonKeyVal.keySet()) {
						jsonObj.put(
							key,
							(rs.getObject(jsonKeyVal.get(key)) != null)
								? rs.getObject(jsonKeyVal.get(key)).toString()
								: ""
						);
					}
					array.put(jsonObj);
					count++;
				}

				if (count == 0) {
					JSONObject empty = getArrangedJson();
					for (String key : jsonKeyVal.keySet()) {
						empty.put(key, "");
					}
					array.put(empty);
				}
			}
		} catch (Exception e) {
			LogUtil.error("SandBoxData", e, "Error loading Data");
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
		return array;
	}

	private JSONObject getArrangedJson() {
		return new JSONObject() {
			@Override
			public JSONObject put(String key, Object value)
				throws JSONException {
				try {
					Field map = JSONObject.class.getDeclaredField("map");
					map.setAccessible(true);
					Object mapValue = map.get(this);
					if (!(mapValue instanceof LinkedHashMap)) {
						map.set(this, new LinkedHashMap<>());
					}
				} catch (NoSuchFieldException | IllegalAccessException e) {
					throw new RuntimeException(e);
				}
				return super.put(key, value);
			}
		};
	}
}
