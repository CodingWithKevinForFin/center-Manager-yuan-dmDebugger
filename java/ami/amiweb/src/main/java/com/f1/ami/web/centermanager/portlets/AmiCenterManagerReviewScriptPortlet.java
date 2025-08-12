package com.f1.ami.web.centermanager.portlets;

import java.util.Map;

import com.f1.ami.amicommon.AmiUtils;
import com.f1.ami.amicommon.msg.AmiCenterQueryDsRequest;
import com.f1.ami.amicommon.msg.AmiCenterQueryDsResponse;
import com.f1.ami.portlets.AmiWebHeaderPortlet;
import com.f1.ami.web.AmiWebFormPortletAmiScriptField;
import com.f1.ami.web.AmiWebService;
import com.f1.ami.web.AmiWebUtils;
import com.f1.ami.web.centermanager.editors.AmiCenterManagerSubmitEditScriptPortlet;
import com.f1.base.Action;
import com.f1.container.ResultMessage;
import com.f1.suite.web.portal.PortletConfig;
import com.f1.suite.web.portal.impl.GridPortlet;
import com.f1.suite.web.portal.impl.form.FormPortlet;
import com.f1.suite.web.portal.impl.form.FormPortletButton;
import com.f1.suite.web.portal.impl.form.FormPortletField;
import com.f1.suite.web.portal.impl.form.FormPortletListener;
import com.f1.suite.web.util.WebHelper;
import com.f1.utils.SH;

public class AmiCenterManagerReviewScriptPortlet extends GridPortlet implements FormPortletListener {
	final private AmiWebService service;
	final private AmiWebHeaderPortlet header;
	final private AmiCenterManagerReviewApplyScriptPortlet owner;
	final private FormPortlet scriptForm;
	final private AmiWebFormPortletAmiScriptField scriptField;
	final private FormPortlet buttonsFp;
	final private FormPortletButton applyButton;
	final private FormPortletButton cancelButton;

	public AmiCenterManagerReviewScriptPortlet(PortletConfig config, AmiCenterManagerReviewApplyScriptPortlet parent) {
		super(config);
		service = AmiWebUtils.getService(getManager());
		owner = parent;
		header = new AmiWebHeaderPortlet(generateConfig());
		header.setInformationHeaderHeight(70);
		header.updateBlurbPortletLayout("Review the SQL Script to be Applied on AMIDB", null);
		header.setShowSearch(false);
		header.setShowBar(false);
		scriptForm = new FormPortlet(generateConfig());
		scriptField = scriptForm.addField(new AmiWebFormPortletAmiScriptField("", getManager(), AmiWebFormPortletAmiScriptField.LANGUAGE_SCOPE_CENTER_SCRIPT));
		scriptField.setLeftPosPx(0).setTopPosPx(20).setHeightPx(500).setWidthPx(770);
		buttonsFp = new FormPortlet(generateConfig());
		buttonsFp.getFormPortletStyle().setLabelsWidth(200);
		buttonsFp.addFormPortletListener(this);
		applyButton = buttonsFp.addButton(new FormPortletButton("Apply"));
		cancelButton = buttonsFp.addButton(new FormPortletButton("Cancel"));
		addChild(header, 0, 0);
		addChild(scriptForm, 0, 1);
		addChild(buttonsFp, 0, 2);
		setRowSize(2, 50);

	}

	@Override
	public void onButtonPressed(FormPortlet portlet, FormPortletButton button) {
		if (button == this.cancelButton) {
			owner.close();
			return;
		} else if (button == this.applyButton) {
			executeSql();
			owner.moveToApplyStage();
		}
	}

	@Override
	public void onFieldValueChanged(FormPortlet portlet, FormPortletField<?> field, Map<String, String> attributes) {

	}

	@Override
	public void onSpecialKeyPressed(FormPortlet formPortlet, FormPortletField<?> field, int keycode, int mask, int cursorPosition) {

	}

	public void setSql(String sql) {
		scriptField.setValue(sql);
	}

	public void executeSql() {
		String sql = scriptField.getValue();
		appendOutput("#44ff44", "\n" + SH.trim(sql));
		AmiCenterQueryDsRequest request = prepareRequest();
		if (request == null)
			return;
		request.setQuery(sql);
		service.sendRequestToBackend(this, request);
	}

	public AmiCenterQueryDsRequest prepareRequest() {
		AmiCenterQueryDsRequest request = getManager().getTools().nw(AmiCenterQueryDsRequest.class);
		request.setLimit(AmiCenterManagerSubmitEditScriptPortlet.DEFAULT_LIMIT);
		request.setTimeoutMs(AmiCenterManagerSubmitEditScriptPortlet.DEFAULT_TIMEOUT);
		request.setQuerySessionKeepAlive(true);
		request.setIsTest(false);
		request.setAllowSqlInjection(AmiCenterManagerSubmitEditScriptPortlet.DEFAULT_ALLOW_SQL_INJECTION);
		request.setInvokedBy(service.getUserName());
		request.setSessionVariableTypes(null);
		request.setSessionVariables(null);
		request.setPermissions(AmiCenterManagerSubmitEditScriptPortlet.DEFAULT_PERMISSION);
		request.setType(AmiCenterQueryDsRequest.TYPE_QUERY);
		request.setOriginType(AmiCenterQueryDsRequest.ORIGIN_FRONTEND_SHELL);
		request.setDatasourceName(AmiCenterManagerSubmitEditScriptPortlet.DEFAULT_DS_NAME);
		return request;
	}

	@Override
	public void onBackendResponse(ResultMessage<Action> result) {
		if (result.getError() != null) {
			getManager().showAlert("Internal Error:" + result.getError().getMessage(), result.getError());
			return;
		}
		AmiCenterQueryDsResponse response = (AmiCenterQueryDsResponse) result.getAction();
		StringBuilder sb = new StringBuilder();

		if (response.getOk()) {
			//only enable finish button if the response is ok
			owner.getApplyPortlet().enableFinishButton(true);
			sb.append("<BR>");
			owner.getApplyPortlet().appendHtml(sb.toString());
			sb.setLength(0);
			if (SH.is(response.getMessage()))
				sb.append(response.getMessage()).append('\n');
			AmiUtils.toMessage(response, service.getFormatterManager().getTimeMillisFormatter().getInner(), sb);
			appendOutput("#ffffff", sb.toString());

			Class<?> returnType = response.getReturnType();
			boolean hasReturnValue = returnType != null && returnType != Void.class;
			if (hasReturnValue) {
				sb.setLength(0);
				Object returnValue = AmiUtils.getReturnValue(response);
				if (returnValue != null)
					returnType = returnValue.getClass();
				sb.append("(").append(this.service.getScriptManager("").forType(returnType));
				sb.append(")");
				String s = AmiUtils.sJson(returnValue);
				if (s != null && s.indexOf('\n') != -1)
					sb.append('\n');
				sb.append(s);
				appendOutput("#FFAAFF", sb.toString());
			}
		} else {
			//if the response is not ok aka some errors occur, then enable back button
			owner.getApplyPortlet().enableBackButton(true);
			sb.setLength(0);
			appendOutput("#ff4444", "\n" + response.getMessage() + "\n");
			sb.setLength(0);
			AmiUtils.toMessage(response, service.getFormatterManager().getTimeMillisFormatter().getInner(), sb);
			appendOutput("#ffffff", sb.toString());
		}

	}

	private void appendOutput(String color, String txt) {
		if (SH.isnt(txt))
			return;
		StringBuilder sb = new StringBuilder();
		sb.append("<span style='color:").append(color).append("'>");
		WebHelper.escapeHtmlNewLineToBr(txt, sb);
		sb.append("</span>");
		owner.getApplyPortlet().appendHtml(sb.toString());
	}

}
