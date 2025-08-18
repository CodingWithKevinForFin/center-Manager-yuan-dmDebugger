package com.f1.ami.web.centermanager.editors;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import com.f1.ami.amicommon.customobjects.AmiScriptClassPluginWrapper;
import com.f1.ami.amicommon.msg.AmiCenterQueryDsRequest;
import com.f1.ami.amicommon.msg.AmiCenterQueryDsResponse;
import com.f1.ami.web.AmiWebFormPortletAmiScriptField;
import com.f1.ami.web.AmiWebScriptManager;
import com.f1.ami.web.AmiWebUtils;
import com.f1.ami.web.centermanager.AmiCenterEntityConsts;
import com.f1.ami.web.centermanager.AmiCenterManagerUtils;
import com.f1.ami.web.centermanager.editors.AmiCenterManagerSubmitEditScriptPortlet;
import com.f1.ami.web.centermanager.graph.nodes.AmiCenterGraphNode;
import com.f1.ami.web.centermanager.graph.nodes.AmiCenterGraphNode_Procedure;
import com.f1.base.Action;
import com.f1.base.Row;
import com.f1.base.Table;
import com.f1.container.ResultMessage;
import com.f1.suite.web.menu.WebMenu;
import com.f1.suite.web.portal.PortletConfig;
import com.f1.suite.web.portal.impl.GridPortlet;
import com.f1.suite.web.portal.impl.TabPortlet;
import com.f1.suite.web.portal.impl.form.FormPortlet;
import com.f1.suite.web.portal.impl.form.FormPortletField;
import com.f1.suite.web.portal.impl.form.FormPortletSelectField;
import com.f1.suite.web.portal.impl.form.FormPortletTextField;
import com.f1.utils.LH;
import com.f1.utils.SH;
import com.f1.utils.string.JavaExpressionParser;
import com.f1.utils.string.Node;
import com.f1.utils.string.node.DeclarationNode;
import com.f1.utils.string.node.MethodNode;
import com.f1.utils.string.sqlnode.AdminNode;
import com.f1.utils.structs.table.derived.BasicDerivedCellParser;
import com.f1.utils.structs.table.derived.DeclaredMethodFactory;

public class AmiCenterManagerEditProcedurePortlet extends AmiCenterManagerAbstractEditCenterObjectPortlet {
	private static final int DEFAULT_ROWHEIGHT = 25;
	private static final int DEFAULT_LEFTPOS = 75; //164
	private static final int DEFAULT_Y_SPACING = 10;
	private static final int DEFAULT_X_SPACING = 65;
	private static final int DEFAULT_TOPPOS = DEFAULT_Y_SPACING;

	//Width consts
	private static final int NAME_WIDTH = 110;
	private static final int TYPE_WIDTH = 80;
	private static final int LOGGING_WIDTH = 80;
	private static final int ARGS_WIDTH = 680;
	//height const
	private static final int OPTION_FORM_HEIGHT = 120;
	private static final int AMISCRIPT_FORM_HEIGHT = 600;

	//padding
	private static final int AMISCRIPT_FORM_PADDING = 0;

	final private FormPortlet form;
	final private TabPortlet scriptTabs;
	final private FormPortletTextField nameField;
	final private FormPortletSelectField<Short> typeField;
	final private AmiWebFormPortletAmiScriptField argsField;
	final private FormPortletSelectField<Short> loggingField;

	final private GridPortlet scriptGrid;
	final private FormPortlet scriptForm;

	final private GridPortlet onStartupScriptGrid;
	final private FormPortlet onStartupScriptForm;
	final private AmiWebFormPortletAmiScriptField scriptField;
	final private AmiWebFormPortletAmiScriptField onStartupScriptField;

	public AmiCenterManagerEditProcedurePortlet(PortletConfig config, boolean isAdd) {
		super(config, isAdd);

		this.form = new FormPortlet(generateConfig());

		//The top row
		this.nameField = this.form.addField(new FormPortletTextField(AmiCenterManagerUtils.formatRequiredField("Name")));
		this.nameField.setWidth(NAME_WIDTH);
		this.nameField.setHeightPx(DEFAULT_ROWHEIGHT);
		this.nameField.setLeftPosPx(DEFAULT_LEFTPOS);
		this.nameField.setTopPosPx(DEFAULT_TOPPOS);

		this.typeField = this.form.addField(new FormPortletSelectField<Short>(short.class, AmiCenterManagerUtils.formatRequiredField("Type")));
		//only one type for timer, so disable
		this.typeField.setDisabled(true);
		this.typeField.addOption(AmiCenterEntityConsts.TIMER_TYPE_CODE_AMISCRIPT, AmiCenterEntityConsts.TIMER_TYPE_AMISCRIPT);
		this.typeField.setLeftPosPx(DEFAULT_LEFTPOS + NAME_WIDTH + DEFAULT_X_SPACING).setTopPosPx(DEFAULT_TOPPOS).setHeightPx(DEFAULT_ROWHEIGHT).setWidthPx(100);

		loggingField = this.form.addField(new FormPortletSelectField(short.class, "logging"));
		loggingField.addOption(AmiCenterEntityConsts.LOGGING_LEVEL_CODE_OFF, AmiCenterEntityConsts.LOGGING_LEVEL_OFF);
		loggingField.addOption(AmiCenterEntityConsts.LOGGING_LEVEL_CODE_ON, AmiCenterEntityConsts.LOGGING_LEVEL_ON);
		loggingField.addOption(AmiCenterEntityConsts.LOGGING_LEVEL_CODE_VERBOSE, AmiCenterEntityConsts.LOGGING_LEVEL_VERBOSE);
		loggingField.setHelp("set the logging level when the timer gets called: The following Logging options are supported:<br>"
				+ "<ul><li>(1). <b style=\"color:blue\"><i>off</i></b> (default): no logging<br></li>"
				+ "<li>(2). <b style=\"color:blue\"><i>on</i></b>: logs the time when the timer is called and when it completes.<br></li>"
				+ "<li>(3). <b style=\"color:blue\"><i>verbose</i></b>: equivalent of using <b><i>show_plan=ON</i></b> in AMIDB. Logs the time that a timer starts and finishes and also each query step.</li></ul>");
		loggingField.setWidthPx(LOGGING_WIDTH);
		loggingField.setHeightPx(DEFAULT_ROWHEIGHT);
		loggingField.setLeftPosPx(DEFAULT_LEFTPOS + NAME_WIDTH + TYPE_WIDTH + DEFAULT_X_SPACING * 3);
		loggingField.setTopPosPx(DEFAULT_TOPPOS);

		if (!isAdd) {
			this.form.addField(enableEditingCheckbox);
			enableEditingCheckbox.setWidth(20).setHeightPx(DEFAULT_ROWHEIGHT).setLeftPosPx(DEFAULT_LEFTPOS + NAME_WIDTH + TYPE_WIDTH + LOGGING_WIDTH + DEFAULT_X_SPACING * 3 + 150)
					.setTopPosPx(DEFAULT_TOPPOS);
		}

		argsField = this.form.addField(new AmiWebFormPortletAmiScriptField(AmiCenterManagerUtils.formatRequiredField("arguments"), getManager(),
				AmiWebFormPortletAmiScriptField.LANGUAGE_SCOPE_CENTER_SCRIPT));
		argsField.setHelp("Variables shared by the timer, a comma delimited list of type varname");
		argsField.setWidthPx(ARGS_WIDTH);
		argsField.setHeightPx(DEFAULT_ROWHEIGHT * 2);
		argsField.setLeftPosPx(DEFAULT_LEFTPOS + 10);
		argsField.setTopPosPx(DEFAULT_TOPPOS + (int) ((DEFAULT_ROWHEIGHT + DEFAULT_Y_SPACING) * 1.5));

		onStartupScriptGrid = new GridPortlet(generateConfig());
		onStartupScriptForm = new FormPortlet(generateConfig());
		onStartupScriptGrid.addChild(onStartupScriptForm);
		onStartupScriptField = onStartupScriptForm.addField(new AmiWebFormPortletAmiScriptField("", getManager(), AmiWebFormPortletAmiScriptField.LANGUAGE_SCOPE_CENTER_SCRIPT));
		onStartupScriptField.setHelp("AmiScript to run when the timer is created");
		onStartupScriptField.setLeftPosPx(AMISCRIPT_FORM_PADDING).setRightPosPx(AMISCRIPT_FORM_PADDING).setBottomPosPx(AMISCRIPT_FORM_PADDING);
		onStartupScriptField.setWidthPx(400);
		onStartupScriptField.setHeightPx(AMISCRIPT_FORM_HEIGHT);
		onStartupScriptField.setTopPosPx(DEFAULT_TOPPOS);

		scriptGrid = new GridPortlet(generateConfig());
		scriptForm = new FormPortlet(generateConfig());
		scriptGrid.addChild(scriptForm);
		scriptField = scriptForm.addField(new AmiWebFormPortletAmiScriptField("", getManager(), AmiWebFormPortletAmiScriptField.LANGUAGE_SCOPE_CENTER_SCRIPT));
		scriptField.setHelp("AmiScript to run when timer is executed");
		scriptField.setLeftPosPx(AMISCRIPT_FORM_PADDING).setRightPosPx(AMISCRIPT_FORM_PADDING).setBottomPosPx(AMISCRIPT_FORM_PADDING);
		scriptField.setWidthPx(400);
		scriptField.setHeightPx(AMISCRIPT_FORM_HEIGHT);
		scriptField.setTopPosPx(DEFAULT_TOPPOS);

		this.scriptTabs = new TabPortlet(generateConfig());
		this.scriptTabs.setIsCustomizable(false);
		this.scriptTabs.addChild("Script", scriptGrid);
		this.scriptTabs.addChild("onStartupScript", onStartupScriptGrid);

		GridPortlet grid = new GridPortlet(generateConfig());
		grid.addChild(form, 0, 0);
		grid.addChild(scriptTabs, 0, 1);
		grid.setRowSize(0, OPTION_FORM_HEIGHT);

		this.addChild(grid, 0, 0);
		this.addChild(buttonsFp, 0, 1);

		setRowSize(1, buttonsFp.getButtonPanelHeight());

		this.form.addFormPortletListener(this);
		this.scriptForm.addFormPortletListener(this);
		this.onStartupScriptForm.addFormPortletListener(this);
		registerMethods();
		registerProcedures();
	}

	public AmiCenterManagerEditProcedurePortlet(PortletConfig config, String sql, AmiCenterGraphNode_Procedure correlationNode) {
		this(config, false);
		this.correlationNode = correlationNode;
		//never allow editing 
		this.typeField.setDisabled(true);
		this.importFromText(sql, new StringBuilder());
		//add form portlet listener
		this.form.addFormPortletListener(this);
		//by default editing is disabled
		enableEdit(false);
	}

	private void parseVars() {
		String[] vars = SH.split(',', this.argsField.getValue());
		JavaExpressionParser jep = new JavaExpressionParser();
		BasicDerivedCellParser cp = new BasicDerivedCellParser(jep);
		AmiWebScriptManager wsm = new AmiWebScriptManager(AmiWebUtils.getService(getManager()), new HashMap<String, AmiScriptClassPluginWrapper>());
		for (String var : vars) {
			try {
				com.f1.utils.string.Node n = jep.parse(var);
				if (n instanceof DeclarationNode) {
					String varName = ((DeclarationNode) n).getVarname();
					String clzzName = ((DeclarationNode) n).getVartype();
					Class<?> clzz = cp.forName(n.getPosition(), argsField.getCenterMethodFactory(), clzzName);
					this.scriptField.addVariable(varName, clzz);
					this.onStartupScriptField.addVariable(varName, clzz);
				}
			} catch (Exception e) {
				if (LH.isFine(log))
					LH.fine(log, this.service.getUserName() + ": Problem with Text ", e);
			}
		}
	}

	@Override
	public WebMenu createMenu(FormPortlet formPortlet, FormPortletField<?> field, int cursorPosition) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void onContextMenu(FormPortlet portlet, String action, FormPortletField node) {
		// TODO Auto-generated method stub

	}

	@Override
	public void onFieldValueChanged(FormPortlet portlet, FormPortletField<?> field, Map<String, String> attributes) {
		super.onFieldValueChanged(portlet, field, attributes);
		if (field == argsField)
			parseVars();
		onFieldChanged(field);

	}

	@Override
	public void onSpecialKeyPressed(FormPortlet formPortlet, FormPortletField<?> field, int keycode, int mask, int cursorPosition) {
		if (field instanceof AmiWebFormPortletAmiScriptField)
			((AmiWebFormPortletAmiScriptField) field).onSpecialKeyPressed(formPortlet, field, keycode, mask, cursorPosition);

	}

	@Override
	public String prepareUseClause() {
		StringBuilder sb = new StringBuilder();
		//requried field
		if (SH.is(argsField.getValue()))
			sb.append(" arguments = ").append(SH.doubleQuote(argsField.getValue()));
		else
			sb.append(" arguments = ").append(AmiCenterEntityConsts.REQUIRED_FEILD_WARNING);
		if (SH.is(scriptField.getValue()))
			sb.append(" script= ").append(SH.doubleQuote(scriptField.getValue()));
		else
			sb.append(" script= ").append(AmiCenterEntityConsts.REQUIRED_FEILD_WARNING);
		if (loggingField.getValue() != AmiCenterEntityConsts.LOGGING_LEVEL_CODE_OFF)
			sb.append(" logging = ").append(SH.doubleQuote(loggingField.getOption(loggingField.getValue()).getName()));

		if (SH.is(onStartupScriptField.getValue()))
			sb.append(" onStartupScript = ").append(SH.doubleQuote(onStartupScriptField.getValue()));

		return sb.toString();
	}

	@Override
	public String preparePreUseClause() {
		StringBuilder sb = new StringBuilder("CREATE PROCEDURE ");
		if (SH.is(nameField.getValue()))
			sb.append(SH.doubleQuote(nameField.getValue()));
		else
			sb.append(AmiCenterEntityConsts.REQUIRED_FEILD_WARNING);
		sb.append(" OFTYPE ").append(typeField.getOption(typeField.getValue()).getName());
		return sb.toString();
	}

	@Override
	public String exportToText() {
		return previewScript();
	}

	@Override
	public void importFromText(String text, StringBuilder sink) {
		AdminNode an = AmiCenterManagerUtils.scriptToAdminNode(text);
		Map<String, String> config = AmiCenterManagerUtils.parseAdminNode_Procedure(an);
		String type = config.get("procedureType");
		typeField.setDefaultValue(AmiCenterManagerUtils.centerObjectTypeToCode(AmiCenterGraphNode.TYPE_PROCEDURE, SH.toUpperCase(type)));
		//only type is "AmiScript" for procedure, no need to update the template

		for (Entry<String, String> e : config.entrySet()) {
			String key = e.getKey();
			String value = e.getValue();
			if ("procedureName".equals(key)) {
				this.nameField.setValue(value);
				this.nameField.setDefaultValue(value);
			} else if ("procedureType".equals(key)) {
				continue;
			} else if ("logging".equals(key)) {
				if (SH.is(value)) {
					this.loggingField.setValue(AmiCenterManagerUtils.toLoggingTypeCode(value));
					this.loggingField.setDefaultValue(AmiCenterManagerUtils.toLoggingTypeCode(value));
				}
			} else if ("arguments".equals(key)) {
				this.argsField.setValue(value);
				this.argsField.setDefaultValue(value);
				parseVars();
			} else if ("script".equals(key)) {
				this.scriptField.setValue(value);
				this.scriptField.setDefaultValue(value);
			} else if ("onStartupScript".equals(key)) {
				this.onStartupScriptField.setValue(value);
				this.onStartupScriptField.setDefaultValue(value);
			}
		}
	}

	@Override
	public void enableEdit(boolean enable) {
		for (FormPortletField<?> fpf : this.form.getFormFields()) {
			if (fpf != this.enableEditingCheckbox)
				fpf.setDisabled(!enable);
			if (fpf == this.argsField)
				AmiCenterManagerUtils.onFieldDisabled(argsField, !enable);
		}
		this.onStartupScriptField.setDisabled(!enable);
		AmiCenterManagerUtils.onFieldDisabled(onStartupScriptField, !enable);
		this.scriptField.setDisabled(!enable);
		AmiCenterManagerUtils.onFieldDisabled(scriptField, !enable);

	}

	private void registerMethods() {
		sendQueryToBackend("SHOW METHODS WHERE DefinedBy==\"USER\"");
	}

	private void registerProcedures() {
		sendQueryToBackend("SHOW FULL PROCEDURES");
	}

	@Override
	public void onBackendResponse(ResultMessage<Action> result) {
		if (result.getError() != null) {
			getManager().showAlert("Internal Error:" + result.getError().getMessage(), result.getError());
			return;
		}
		Action a = result.getRequestMessage().getAction();
		String query = null;
		if (a instanceof AmiCenterQueryDsRequest) {
			AmiCenterQueryDsRequest request = (AmiCenterQueryDsRequest) a;
			query = request.getQuery();
		}
		AmiCenterQueryDsResponse response = (AmiCenterQueryDsResponse) result.getAction();
		if (response.getOk()) {
			List<Table> tables = response.getTables();
			if (tables.size() == 1) {
				Table t = tables.get(0);
				JavaExpressionParser jep = new JavaExpressionParser();
				BasicDerivedCellParser cp = new BasicDerivedCellParser(jep);
				if (query.startsWith("SHOW METHODS")) {
					for (Row r : t.getRows()) {
						String methodName = (String) r.get("MethodName");
						String definition = (String) r.get("Definition");
						String returnType = (String) r.get("ReturnType");
						MethodNode mn = (MethodNode) cp.getExpressionParser().parse(definition);
						int paramsCnt = mn.getParamsCount();
						Node[] ns = mn.getParamsToArray();
						String[] paramNames = new String[paramsCnt];
						Class[] paramClzzs = new Class[paramsCnt];
						for (int i = 0; i < paramsCnt; i++) {
							DeclarationNode n = (DeclarationNode) ns[i];
							String name = n.getVarname();
							String type = n.getVartype();
							paramNames[i] = name;
							try {
								paramClzzs[i] = this.scriptField.getCenterMethodFactory().forName(type);
							} catch (ClassNotFoundException e1) {
								LH.warning(log, "Class Not found" + name);
							}
						}
						try {
							Class<?> returnTypeClzz = this.scriptField.getCenterMethodFactory().forName(returnType);
							DeclaredMethodFactory dmf = new DeclaredMethodFactory(returnTypeClzz, methodName, paramNames, paramClzzs, (byte) 0);
							scriptField.addMethodFactory(dmf);
							onStartupScriptField.addMethodFactory(dmf);
						} catch (ClassNotFoundException e) {
							e.printStackTrace();
						}

					}
				} else {
					for (Row r : t.getRows()) {
						String methodName = (String) r.get("ProcedureName");
						String args = (String) r.get("Arguments");
						String returnType = (String) r.get("ReturnType");
						String[] params = SH.split(",", args);
						//						String[] params = SH.isnt(args) ? new String[0] : SH.split(",",args);
						int paramsCnt = params.length;
						String[] paramNames = new String[paramsCnt];
						Class[] paramClzzs = new Class[paramsCnt];
						for (int i = 0; i < paramsCnt; i++) {
							String param = params[i];
							String name = SH.beforeFirst(param, " ");
							String type = SH.afterFirst(param, " ").contains(" ") ? SH.beforeFirst(SH.afterFirst(param, " "), " ") : SH.afterFirst(param, " ");
							paramNames[i] = name;
							try {
								paramClzzs[i] = this.scriptField.getCenterMethodFactory().forName(type);
							} catch (ClassNotFoundException e1) {
								LH.warning(log, "Class Not found" + name);
							}
						}
						try {
							Class<?> returnTypeClzz = this.scriptField.getCenterMethodFactory().forName(returnType);
							DeclaredMethodFactory dmf = new DeclaredMethodFactory(returnTypeClzz, methodName, paramNames, paramClzzs, (byte) 0);
							scriptField.registerProcedure(dmf);
							onStartupScriptField.registerProcedure(dmf);
						} catch (ClassNotFoundException e) {
							e.printStackTrace();
						}

					}
				}

			}
		}
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

	@Override
	public String previewEdit() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public boolean ensureCanProceedWithApply() {
		if(SH.isnt(this.nameField.getValue())) {
			AmiCenterManagerUtils.popDialog(service, "The trigger name field cannot be empty", "Error Applying Changes");
			return false;
		}
		return true;
		
	}

}
