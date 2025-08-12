package com.f1.ami.web.centermanager.editors;

import java.util.List;
import java.util.Map;

import com.f1.ami.amicommon.AmiUtils;
import com.f1.ami.amicommon.msg.AmiCenterQueryDsRequest;
import com.f1.ami.amicommon.msg.AmiCenterQueryDsResponse;
import com.f1.ami.portlets.AmiWebHeaderPortlet;
import com.f1.ami.web.AmiWebFormPortletAmiScriptField;
import com.f1.ami.web.AmiWebService;
import com.f1.ami.web.AmiWebSpecialPortlet;
import com.f1.base.Action;
import com.f1.base.Table;
import com.f1.container.ResultMessage;
import com.f1.suite.web.portal.PortletConfig;
import com.f1.suite.web.portal.impl.ConfirmDialog;
import com.f1.suite.web.portal.impl.ConfirmDialogListener;
import com.f1.suite.web.portal.impl.ConfirmDialogPortlet;
import com.f1.suite.web.portal.impl.GridPortlet;
import com.f1.suite.web.portal.impl.HtmlPortlet;
import com.f1.suite.web.portal.impl.HtmlPortlet.Callback;
import com.f1.suite.web.portal.impl.HtmlPortletListener;
import com.f1.suite.web.portal.impl.MultiDividerPortlet;
import com.f1.suite.web.portal.impl.form.FormPortlet;
import com.f1.suite.web.portal.impl.form.FormPortletButton;
import com.f1.suite.web.portal.impl.form.FormPortletField;
import com.f1.suite.web.portal.impl.form.FormPortletListener;
import com.f1.suite.web.util.WebHelper;
import com.f1.utils.SH;

public class AmiCenterManagerSubmitEditScriptPortlet extends GridPortlet implements AmiWebSpecialPortlet, ConfirmDialogListener, FormPortletListener, HtmlPortletListener {
	public static final int DEFAULT_PORTLET_WIDTH = 550;
	public static final int DEFAULT_PORTLET_HEIGHT = 520;
	public static final boolean DEFAULT_ALLOW_SQL_INJECTION = Boolean.FALSE;
	public static final String DEFAULT_DS_NAME = "AMI";
	public static final byte DEFAULT_PERMISSION = (byte) 15;
	//Backend config
	public static final int DEFAULT_LIMIT = 10000;
	public static final int DEFAULT_TIMEOUT = 60000;

	private long sessionId = -1;

	final private AmiWebService service;

	final private FormPortlet form;//contains all the buttons
	final private MultiDividerPortlet div;

	//buttons
	private FormPortletButton confirmTriggerButton;
	private FormPortletButton undoButton;

	//div children
	//child1
	final private FormPortlet previewForm;
	final private FormPortletField<String> previewField;

	//child2
	final private HtmlPortlet resultForm;

	public AmiCenterManagerSubmitEditScriptPortlet(AmiWebService service, PortletConfig config, String previewScript) {
		super(config);
		this.service = service;
		this.div = new MultiDividerPortlet(generateConfig(), false);
		this.form = new FormPortlet(generateConfig());

		AmiWebHeaderPortlet header = new AmiWebHeaderPortlet(generateConfig());
		header.setShowSearch(false);
		header.updateBlurbPortletLayout("Submit Trigger", "");
		header.setShowLegend(false);
		header.setInformationHeaderHeight(80);
		header.getBarFormPortlet().addFormPortletListener(this);
		this.confirmTriggerButton = header.getBarFormPortlet().addButton(new FormPortletButton("Confirm").setCssStyle("_bg=#4edd49|_fg=#000000"));
		this.undoButton = header.getBarFormPortlet().addButton(new FormPortletButton("Back").setCssStyle("_bg=#b3e5fc|_fg=#000000"));
		addChild(header, 0, 0);
		//div children init
		this.previewForm = new FormPortlet(generateConfig());
		this.previewField = this.previewForm.addField(new AmiWebFormPortletAmiScriptField("Script", getManager(), ""));

		this.previewField.setLeftTopWidthHeightPx(60, 20, (int) (DEFAULT_PORTLET_WIDTH * 0.85), 0);
		this.previewField.setHeightPct(0.8d);
		this.previewField.setValue(previewScript);

		this.resultForm = new HtmlPortlet(generateConfig());
		this.resultForm.addListener(this);
		this.resultForm.setJavascript("scrollToBottom()");

		this.resultForm.setCssStyle("style.fontFamily=courier|_bg=#000000|_fg=#44FF44|style.overflow=scroll");

		this.div.addChild(this.previewForm);
		this.div.addChild(resultForm);
		this.div.setWeights(new double[] { 2, 1 });
		this.div.setThickness(2);
		this.div.setColor("#00000");

		this.addChild(this.div, 0, 1);

		this.previewForm.addFormPortletListener(this);
		sendAuth();
	}

	private void sendAuth() {
		AmiCenterQueryDsRequest request = prepareRequest();
		if (request == null)
			return;
		service.sendRequestToBackend(this, request);
	}

	@Override
	public void onButtonPressed(FormPortlet portlet, FormPortletButton button) {
		if (button == this.confirmTriggerButton) {
			this.previewField.setTopPosPx(0);
			String query = this.previewField.getValue();
			if (SH.isnt(query))
				return;
			AmiCenterQueryDsRequest request = prepareRequest();
			if (request == null)
				return;
			request.setQuery(query);
			request.setQuerySessionId(this.sessionId);
			service.sendRequestToBackend(this, request);
			appendOutput("#44ff44", "\n" + SH.trim(query));
		} else if (button == this.undoButton)
			this.close();

	}

	@Override
	public void onFieldValueChanged(FormPortlet portlet, FormPortletField<?> field, Map<String, String> attributes) {

	}

	@Override
	public void onSpecialKeyPressed(FormPortlet formPortlet, FormPortletField<?> field, int keycode, int mask, int cursorPosition) {
		// TODO Auto-generated method stub

	}

	@Override
	public void onUserClick(HtmlPortlet portlet) {
		// TODO Auto-generated method stub

	}

	@Override
	public void onUserCallback(HtmlPortlet htmlPortlet, String id, int mouseX, int mouseY, Callback cb) {
		// TODO Auto-generated method stub

	}

	@Override
	public void onHtmlChanged(String old, String nuw) {
		// TODO Auto-generated method stub

	}

	private AmiCenterQueryDsRequest prepareRequest() {
		AmiCenterQueryDsRequest request = getManager().getTools().nw(AmiCenterQueryDsRequest.class);

		request.setLimit(DEFAULT_LIMIT);
		request.setTimeoutMs(DEFAULT_TIMEOUT);
		request.setQuerySessionKeepAlive(true);
		request.setIsTest(false);
		request.setAllowSqlInjection(DEFAULT_ALLOW_SQL_INJECTION);
		request.setInvokedBy(service.getUserName());
		request.setSessionVariableTypes(null);
		request.setSessionVariables(null);
		request.setPermissions(DEFAULT_PERMISSION);
		request.setType(AmiCenterQueryDsRequest.TYPE_QUERY);
		request.setOriginType(AmiCenterQueryDsRequest.ORIGIN_FRONTEND_SHELL);
		request.setDatasourceName(DEFAULT_DS_NAME);
		return request;
	}

	@Override
	public void onBackendResponse(ResultMessage<Action> result) {
		if (result.getError() != null) {
			getManager().showAlert("Internal Error:" + result.getError().getMessage(), result.getError());
			return;
		}
		AmiCenterQueryDsResponse response = (AmiCenterQueryDsResponse) result.getAction();
		if (sessionId == -1) {
			this.sessionId = response.getQuerySessionId();
			if (this.sessionId >= 0) {
				appendOutput("#ffffff", "Successfully connected to Center, Session ID " + this.sessionId);
			}
		}
		StringBuilder sb = new StringBuilder();

		if (response.getOk()) {
			sb.append("<BR>");
			List<Table> tables = response.getTables();
			this.resultForm.appendHtml(sb.toString());
			sb.setLength(0);
			if (SH.is(response.getMessage()))
				sb.append(response.getMessage()).append('\n');
			AmiUtils.toMessage(response, service.getFormatterManager().getTimeMillisFormatter().getInner(), sb);
			appendOutput("#ffffff", sb.toString());

			//NOTE This code should be VERY similar to  AmiCenterConsolCmd_Sql
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
			//finally pop out the alert dialog 
			if (tables != null) {
				this.getManager().showDialog("Close",
						new ConfirmDialogPortlet(generateConfig(), "Close Portlet", ConfirmDialogPortlet.TYPE_OK_CANCEL, this).setCallback(ConfirmDialog.ID_YES));
			}
			//ConfirmDialog
		} else {
			sb.setLength(0);
			appendOutput("#ff4444", "\n" + response.getMessage() + "\n");
			sb.setLength(0);
			AmiUtils.toMessage(response, service.getFormatterManager().getTimeMillisFormatter().getInner(), sb);
			appendOutput("#ffffff", sb.toString());
		}

		//setIsRunning(false);
	}

	private void appendOutput(String color, String txt) {
		if (SH.isnt(txt))
			return;
		StringBuilder sb = new StringBuilder();
		sb.append("<span style='color:").append(color).append("'>");
		WebHelper.escapeHtmlNewLineToBr(txt, sb);
		sb.append("</span>");
		this.resultForm.appendHtml(sb.toString());
		//		this.outputField.setValue(sb.toString());
	}

	@Override
	public boolean onButton(ConfirmDialog source, String id) {
		if (ConfirmDialogPortlet.ID_YES.equals(id)) {
			String callback = source.getCallback();
			if (ConfirmDialog.ID_YES.equals(callback)) {
				this.close();
			}
		}
		return true;
	}
}
