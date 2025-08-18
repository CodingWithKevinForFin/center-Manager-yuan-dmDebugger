package com.f1.ami.web.centermanager.editors;

import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

import com.f1.ami.amicommon.msg.AmiCenterQueryDsRequest;
import com.f1.ami.web.AmiWebService;
import com.f1.ami.web.AmiWebUtils;
import com.f1.ami.web.centermanager.AmiCenterEntityConsts;
import com.f1.ami.web.centermanager.AmiCenterManagerUtils;
import com.f1.ami.web.centermanager.editors.AmiCenterManagerSubmitEditScriptPortlet;
import com.f1.ami.web.centermanager.graph.nodes.AmiCenterGraphNode;
import com.f1.ami.web.centermanager.portlets.AmiCenterManagerReviewApplyScriptPortlet;
import com.f1.base.Action;
import com.f1.container.ResultMessage;
import com.f1.suite.web.portal.PortletConfig;
import com.f1.suite.web.portal.impl.GridPortlet;
import com.f1.suite.web.portal.impl.form.FormPortlet;
import com.f1.suite.web.portal.impl.form.FormPortletButton;
import com.f1.suite.web.portal.impl.form.FormPortletCheckboxField;
import com.f1.suite.web.portal.impl.form.FormPortletContextMenuFactory;
import com.f1.suite.web.portal.impl.form.FormPortletContextMenuListener;
import com.f1.suite.web.portal.impl.form.FormPortletField;
import com.f1.suite.web.portal.impl.form.FormPortletListener;
import com.f1.utils.CH;
import com.f1.utils.LH;
import com.f1.utils.OH;
import com.f1.utils.SH;
import com.f1.utils.concurrent.IdentityHashSet;
import com.f1.utils.converter.json2.ObjectToJsonConverter;

public abstract class AmiCenterManagerAbstractEditCenterObjectPortlet extends GridPortlet
		implements FormPortletContextMenuFactory, FormPortletContextMenuListener, FormPortletListener {
	public static final int DEFAULT_ROWHEIGHT = 25;
	public static final int DEFAULT_LEFTPOS = 75; //164
	public static final int DEFAULT_Y_SPACING = 12;
	public static final int DEFAULT_X_SPACING = 90;
	public static final int DEFAULT_TOPPOS = DEFAULT_Y_SPACING;

	public static final Logger log = LH.get();
	public static final Set<String> SYSTEM_TABLES = CH.s("__CENTER", "__COLUMN", "__COMMAND", "__CONNECTION", "__DATASOURCE", "__DATASOURCE_TYPE", "__DBO", "__INDEX", "__PLUGIN",
			"__PROCEDURE", "__PROPERTY", "__RELAY", "__REPLICATION", "__RESOURCE", "__STATS", "__TABLE", "__TIMER", "__TRIGGER");

	//Width consts
	public static final int NAME_WIDTH = 250;
	public static final int TYPE_WIDTH = 100;
	public static final int PRIORITY_WIDTH = 50;
	public static final int ON_WIDTH = 700;
	public static final int TIMEOUT_WIDTH = 100;
	public static final int LIMIT_WIDTH = 100;
	public static final int LOGGING_WIDTH = 80;
	public static final int VARS_WIDTH = 700;

	//height const
	public static final int OPTION_FORM_HEIGHT = 120;
	public static final int AMISCRIPT_FORM_HEIGHT = 600;

	//padding
	public static final int AMISCRIPT_FORM_PADDING = 0;

	protected final AmiWebService service;
	protected boolean isAdd = false;

	//buttons
	final protected FormPortlet buttonsFp;
	protected AmiCenterGraphNode correlationNode;
	protected final FormPortletButton applyButton;
	protected final FormPortletButton cancelButton;
	protected final FormPortletButton resetButton;
	protected final FormPortletButton importExportButton;
	protected final static ObjectToJsonConverter JSON_CONVERTER = new ObjectToJsonConverter();
	static {
		JSON_CONVERTER.setIgnoreUnconvertable(true);
		JSON_CONVERTER.setStrictValidation(true);
		JSON_CONVERTER.setTreatNanAsNull(false);
		JSON_CONVERTER.setCompactMode(false);
	}

	//fields needed to query the backend
	protected long sessionId = -1;

	//enable editing
	protected final FormPortletCheckboxField enableEditingCheckbox;
	protected final IdentityHashSet<FormPortletField> editedFields = new IdentityHashSet<FormPortletField>();

	public AmiCenterManagerAbstractEditCenterObjectPortlet(PortletConfig config, boolean isAdd) {
		super(config);
		this.isAdd = isAdd;
		this.enableEditingCheckbox = isAdd ? null : new FormPortletCheckboxField("<i>Enable Editing</i>");
		if (!isAdd) {
			this.enableEditingCheckbox.setDefaultValue(false);
			this.enableEditingCheckbox.setBgColor("#bab0b0");
			this.enableEditingCheckbox.setName(AmiCenterEntityConsts.GROUP_NAME_SKIP_ONFIELDCHANGED);
		}

		this.service = AmiWebUtils.getService(config.getPortletManager());

		this.buttonsFp = new FormPortlet(generateConfig());
		this.buttonsFp.getFormPortletStyle().setLabelsWidth(200);
		this.buttonsFp.setMenuFactory(this);
		this.buttonsFp.addMenuListener(this);
		this.buttonsFp.addFormPortletListener(this);

		this.applyButton = buttonsFp.addButton(new FormPortletButton("Apply"));
		this.cancelButton = buttonsFp.addButton(new FormPortletButton("Cancel"));
		if (!isAdd)
			this.resetButton = buttonsFp.addButton(new FormPortletButton("Reset"));
		else
			this.resetButton = null;
		this.importExportButton = buttonsFp.addButton(new FormPortletButton("Import/Export"));

	}

	@Override
	public void onButtonPressed(FormPortlet portlet, FormPortletButton button) {
		if (button == this.cancelButton) {
			close();
			return;
		} else if (button == this.importExportButton) {
			getManager().showDialog("Export/Import Editor Script", new AmiCenterManagerScriptExportPortlet(generateConfig(), this));
		} else if (button == this.resetButton) {
			revertEdit();
		} else if (button == this.applyButton) {
			if(ensureCanProceedWithApply()) {
				String sql = isAdd ? previewScript() : previewEdit();
				getManager().showDialog("Apply SQL", new AmiCenterManagerReviewApplyScriptPortlet(generateConfig(), sql), 1000, 750);
			}
			

		}
	}

	public AmiCenterGraphNode getCorrelationNode() {
		return this.correlationNode;
	}

	protected void revertEdit() {
		for (FormPortletField i : CH.l(this.editedFields)) {
			Object orig = i.getDefaultValue();
			i.setValue(orig);
			onFieldChanged(i);
		}
	}
	//this checks for any field that has been edited
	public void onFieldChanged(FormPortletField<?> field) {
		if (AmiCenterEntityConsts.GROUP_NAME_SKIP_ONFIELDCHANGED.equals(field.getName()) || this.isAdd)
			return;
		boolean hadNoChanges = this.editedFields.isEmpty();
		Object orig = field.getDefaultValue();
		Object now = field.getValue();
		if (OH.eq(orig, now) || (SH.isnt(now) && orig == null)) {
			this.editedFields.remove(field);
			AmiCenterManagerUtils.onFieldEdited(field, false);
		} else {
			this.editedFields.add(field);
			AmiCenterManagerUtils.onFieldEdited(field, true);
		}
		boolean hasNoChanges = this.editedFields.isEmpty();
		if (hasNoChanges != hadNoChanges) {
			this.applyButton.setEnabled(!hasNoChanges);
			this.cancelButton.setEnabled(!hasNoChanges);
		}
	}

	public String previewScript() {
		if (SH.is(prepareUseClause()))
			return preparePreUseClause() + " USE " + prepareUseClause();
		return preparePreUseClause();
	}

	abstract public String previewEdit();

	@Override
	public void onFieldValueChanged(FormPortlet portlet, FormPortletField<?> field, Map<String, String> attributes) {
		if (field == this.enableEditingCheckbox) {
			enableEdit(this.enableEditingCheckbox.getBooleanValue());
		}

	}

	//The abilities to query the backend
	@Override
	public void onBackendResponse(ResultMessage<Action> result) {
		if (result.getError() != null) {
			getManager().showAlert("Internal Error:" + result.getError().getMessage(), result.getError());
			return;
		}
	}

	@Override
	public void close() {
		super.close();
		this.service.getAmiCenterManagerEditorsManager().onPortletClosed(this);
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

	protected void sendQueryToBackend(String query) {
		if (SH.isnt(query))
			return;
		AmiCenterQueryDsRequest request = prepareRequest();
		if (request == null)
			return;
		request.setQuery(query);
		request.setQuerySessionId(this.sessionId);
		service.sendRequestToBackend(this, request);
	}

	public static String setToString(Set<String> source) {
		StringBuilder sb = new StringBuilder();
		Iterator<String> i = source.iterator();
		while (i.hasNext()) {
			sb.append(i.next());
			if (i.hasNext())
				sb.append(',');
		}
		return sb.toString();
	}

	abstract public String prepareUseClause();

	abstract public String preparePreUseClause();

	abstract public String exportToText();

	abstract public void importFromText(String text, StringBuilder sink);

	abstract public void enableEdit(boolean enable);
	
	//ensure all valid inputs before hitting apply(e.g all required fields are filled in)
	abstract public boolean ensureCanProceedWithApply();
}
