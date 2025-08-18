package com.f1.ami.web.centermanager.editors;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.logging.Logger;

import com.f1.ami.amicommon.customobjects.AmiScriptClassPluginWrapper;
import com.f1.ami.amicommon.msg.AmiCenterQueryDsRequest;
import com.f1.ami.amicommon.msg.AmiCenterQueryDsResponse;
import com.f1.ami.web.AmiWebFormPortletAmiScriptField;
import com.f1.ami.web.AmiWebScriptManager;
import com.f1.ami.web.AmiWebService;
import com.f1.ami.web.AmiWebUtils;
import com.f1.ami.web.centermanager.AmiCenterEntityConsts;
import com.f1.ami.web.centermanager.AmiCenterManagerUtils;
import com.f1.ami.web.centermanager.graph.nodes.AmiCenterGraphNode;
import com.f1.ami.web.centermanager.graph.nodes.AmiCenterGraphNode_Timer;
import com.f1.base.Action;
import com.f1.base.Row;
import com.f1.base.Table;
import com.f1.container.ResultMessage;
import com.f1.suite.web.menu.WebMenu;
import com.f1.suite.web.menu.impl.BasicWebMenu;
import com.f1.suite.web.menu.impl.BasicWebMenuLink;
import com.f1.suite.web.portal.PortletConfig;
import com.f1.suite.web.portal.impl.GridPortlet;
import com.f1.suite.web.portal.impl.TabManager;
import com.f1.suite.web.portal.impl.TabPortlet;
import com.f1.suite.web.portal.impl.TabPortlet.Tab;
import com.f1.suite.web.portal.impl.form.FormPortlet;
import com.f1.suite.web.portal.impl.form.FormPortletField;
import com.f1.suite.web.portal.impl.form.FormPortletSelectField;
import com.f1.suite.web.portal.impl.form.FormPortletTextField;
import com.f1.utils.LH;
import com.f1.utils.OH;
import com.f1.utils.SH;
import com.f1.utils.string.JavaExpressionParser;
import com.f1.utils.string.Node;
import com.f1.utils.string.node.DeclarationNode;
import com.f1.utils.string.node.MethodNode;
import com.f1.utils.string.sqlnode.AdminNode;
import com.f1.utils.structs.table.derived.BasicDerivedCellParser;
import com.f1.utils.structs.table.derived.DeclaredMethodFactory;

public class AmiCenterManagerEditTimerPortlet extends AmiCenterManagerAbstractEditCenterObjectPortlet implements TabManager {
	private static final Logger log = LH.get();

	final private AmiWebService service;
	final private FormPortlet form;
	final private TabPortlet scriptTabs;
	final private FormPortletTextField nameField;
	final private FormPortletSelectField<Short> timerTypeField;
	final private FormPortletTextField timerOnField;
	final private FormPortletTextField timerPriorityField;
	final private FormPortletTextField timerTimeoutField;
	final private FormPortletTextField timerLimitField;
	final private FormPortletSelectField<Short> timerLoggingField;
	final private AmiWebFormPortletAmiScriptField timerVarsField;

	final private GridPortlet scriptGrid;
	final private FormPortlet scriptForm;

	final private GridPortlet onStartupScriptGrid;
	final private FormPortlet onStartupScriptForm;
	//final private AmiWebFormPortletAmiScriptField timerScriptField;
	final private AmiWebFormPortletAmiScriptField timerScriptField;
	final private AmiWebFormPortletAmiScriptField timerOnStartupScriptField;
	public static final HashMap<String, FormPortletField<?>> FIELDS_BY_NAME = new HashMap<String, FormPortletField<?>>();

	public AmiCenterManagerEditTimerPortlet(PortletConfig config, boolean isAdd) {
		super(config, isAdd);
		this.service = AmiWebUtils.getService(getManager());
		this.form = new FormPortlet(generateConfig());

		//The top row
		this.nameField = this.form.addField(new FormPortletTextField(AmiCenterManagerUtils.formatRequiredField("Name")));
		//this.nameField.setId("name");
		FIELDS_BY_NAME.put("name", this.nameField);
		this.nameField.setWidth(NAME_WIDTH);
		this.nameField.setHeightPx(DEFAULT_ROWHEIGHT);
		this.nameField.setLeftPosPx(DEFAULT_LEFTPOS);
		this.nameField.setTopPosPx(DEFAULT_TOPPOS);

		this.timerTypeField = this.form.addField(new FormPortletSelectField<Short>(short.class, AmiCenterManagerUtils.formatRequiredField("Type")));
		FIELDS_BY_NAME.put("type", this.timerTypeField);
		//only one type for timer, so disable
		this.timerTypeField.setDisabled(true);
		this.timerTypeField.addOption(AmiCenterEntityConsts.TIMER_TYPE_CODE_AMISCRIPT, AmiCenterEntityConsts.TIMER_TYPE_AMISCRIPT);
		this.timerTypeField.setLeftPosPx(DEFAULT_LEFTPOS + NAME_WIDTH + (DEFAULT_X_SPACING - 20)).setTopPosPx(DEFAULT_TOPPOS).setHeightPx(DEFAULT_ROWHEIGHT).setWidthPx(100);

		this.timerOnField = this.form.addField(new FormPortletTextField(AmiCenterManagerUtils.formatRequiredField("ON")));
		//this.timerOnField.setId("on");
		FIELDS_BY_NAME.put("on", this.timerOnField);
		this.timerOnField.setHelp("either:<br>" + "<ul><li>(1). A positive number defining the period in milliseconds between timer executions.</li>"
				+ "  <li>(2). Empty string (\"\") to never run timer, useful for timers that should just run at startup, see <i style=\"color:blue\">onStartupScript</i></li>"
				+ "  <li>(3). Crontab style entry declaring the schedule of when the timer should be execute:</li></ul>");
		this.timerOnField.setWidthPx(100);
		this.timerOnField.setHeightPx(DEFAULT_ROWHEIGHT);
		this.timerOnField.setLeftPosPx(DEFAULT_LEFTPOS + NAME_WIDTH + TYPE_WIDTH + 2 * (DEFAULT_X_SPACING - 30));
		this.timerOnField.setTopPosPx(DEFAULT_TOPPOS);

		if (!isAdd) {
			this.form.addField(enableEditingCheckbox);
			enableEditingCheckbox.setWidth(20).setHeightPx(DEFAULT_ROWHEIGHT).setLeftPosPx(DEFAULT_LEFTPOS + NAME_WIDTH + TYPE_WIDTH + (DEFAULT_X_SPACING - 10) * 3 + 85)
					.setTopPosPx(DEFAULT_TOPPOS);
		}
		this.timerPriorityField = this.form.addField(new FormPortletTextField("PRIORITY"));
		//this.timerPriorityField.setId("priority");
		FIELDS_BY_NAME.put("priority", this.timerPriorityField);
		this.timerPriorityField.setHelp("a number, timers with lowest value are executed first. Only considered when two or more timers have the same exact scheduled time");
		this.timerPriorityField.setWidthPx(PRIORITY_WIDTH);
		this.timerPriorityField.setHeightPx(DEFAULT_ROWHEIGHT);
		this.timerPriorityField.setLeftPosPx(DEFAULT_LEFTPOS);
		this.timerPriorityField.setTopPosPx(DEFAULT_TOPPOS + (DEFAULT_ROWHEIGHT + DEFAULT_Y_SPACING) * 1);

		//2nd row
		this.timerTimeoutField = this.form.addField(new FormPortletTextField("timeout"));
		//this.timerTimeoutField = new FormPortletTextField("timeout");
		FIELDS_BY_NAME.put("timeout", this.timerTimeoutField);
		this.timerTimeoutField.setHelp("Timeout in milliseconds, default is 100000 (100 seconds)");
		this.timerTimeoutField.setWidthPx(TIMEOUT_WIDTH);
		this.timerTimeoutField.setHeightPx(DEFAULT_ROWHEIGHT);
		this.timerTimeoutField.setLeftPosPx(DEFAULT_LEFTPOS + PRIORITY_WIDTH + (DEFAULT_X_SPACING - 10));
		this.timerTimeoutField.setTopPosPx(DEFAULT_TOPPOS + (DEFAULT_ROWHEIGHT + DEFAULT_Y_SPACING) * 1);

		this.timerLimitField = this.form.addField(new FormPortletTextField("limit"));
		FIELDS_BY_NAME.put("limit", this.timerLimitField);
		timerLimitField.setHelp("Row limit for queries, default is 10000");
		this.timerLimitField.setWidthPx(LIMIT_WIDTH);
		this.timerLimitField.setHeightPx(DEFAULT_ROWHEIGHT);
		this.timerLimitField.setLeftPosPx(DEFAULT_LEFTPOS + PRIORITY_WIDTH + TIMEOUT_WIDTH + (DEFAULT_X_SPACING - 10) * 2);
		this.timerLimitField.setTopPosPx(DEFAULT_TOPPOS + (DEFAULT_ROWHEIGHT + DEFAULT_Y_SPACING) * 1);

		timerLoggingField = this.form.addField(new FormPortletSelectField(short.class, "logging"));
		FIELDS_BY_NAME.put("logging", this.timerLoggingField);
		timerLoggingField.addOption(AmiCenterEntityConsts.LOGGING_LEVEL_CODE_OFF, AmiCenterEntityConsts.LOGGING_LEVEL_OFF);
		timerLoggingField.addOption(AmiCenterEntityConsts.LOGGING_LEVEL_CODE_ON, AmiCenterEntityConsts.LOGGING_LEVEL_ON);
		timerLoggingField.addOption(AmiCenterEntityConsts.LOGGING_LEVEL_CODE_VERBOSE, AmiCenterEntityConsts.LOGGING_LEVEL_VERBOSE);
		timerLoggingField.setHelp("set the logging level when the timer gets called: The following Logging options are supported:<br>"
				+ "<ul><li>(1). <b style=\"color:blue\"><i>off</i></b> (default): no logging<br></li>"
				+ "<li>(2). <b style=\"color:blue\"><i>on</i></b>: logs the time when the timer is called and when it completes.<br></li>"
				+ "<li>(3). <b style=\"color:blue\"><i>verbose</i></b>: equivalent of using <b><i>show_plan=ON</i></b> in AMIDB. Logs the time that a timer starts and finishes and also each query step.</li></ul>");
		timerLoggingField.setWidthPx(LOGGING_WIDTH);
		timerLoggingField.setHeightPx(DEFAULT_ROWHEIGHT);
		timerLoggingField.setLeftPosPx(DEFAULT_LEFTPOS + PRIORITY_WIDTH + TIMEOUT_WIDTH + LIMIT_WIDTH + (DEFAULT_X_SPACING - 10) * 3);
		timerLoggingField.setTopPosPx(DEFAULT_TOPPOS + (DEFAULT_ROWHEIGHT + DEFAULT_Y_SPACING) * 1);

		//last row(3rd row)
		timerVarsField = this.form.addField(new AmiWebFormPortletAmiScriptField("vars", getManager(), ""));
		//timerVarsField.setId("vars");
		FIELDS_BY_NAME.put("vars", this.timerVarsField);
		timerVarsField.setHelp("Variables shared by the timer, a comma delimited list of type varname");
		timerVarsField.setWidthPx(VARS_WIDTH);
		timerVarsField.setHeightPx((DEFAULT_ROWHEIGHT - 10) * 2);
		timerVarsField.setLeftPosPx(DEFAULT_LEFTPOS);
		timerVarsField.setTopPosPx(DEFAULT_TOPPOS + (DEFAULT_ROWHEIGHT + DEFAULT_Y_SPACING) * 2);

		//script tab
		onStartupScriptGrid = new GridPortlet(generateConfig());
		onStartupScriptForm = new FormPortlet(generateConfig());
		onStartupScriptGrid.addChild(onStartupScriptForm);

		timerOnStartupScriptField = onStartupScriptForm
				.addField(new AmiWebFormPortletAmiScriptField("", getManager(), AmiWebFormPortletAmiScriptField.LANGUAGE_SCOPE_CENTER_SCRIPT));
		//timerOnStartupScriptField.setId("onStartupScript");
		FIELDS_BY_NAME.put("onStartupScript", timerOnStartupScriptField);
		timerOnStartupScriptField.setHelp("AmiScript to run when the timer is created");
		timerOnStartupScriptField.setLeftPosPx(AMISCRIPT_FORM_PADDING).setRightPosPx(AMISCRIPT_FORM_PADDING).setBottomPosPx(AMISCRIPT_FORM_PADDING);
		timerOnStartupScriptField.setWidthPx(400);
		timerOnStartupScriptField.setHeightPx(AMISCRIPT_FORM_HEIGHT);
		timerOnStartupScriptField.setTopPosPx(DEFAULT_TOPPOS);

		scriptGrid = new GridPortlet(generateConfig());
		scriptForm = new FormPortlet(generateConfig());
		scriptGrid.addChild(scriptForm);

		timerScriptField = scriptForm.addField(new AmiWebFormPortletAmiScriptField("", getManager(), AmiWebFormPortletAmiScriptField.LANGUAGE_SCOPE_CENTER_SCRIPT));
		//timerScriptField.setId("script");
		FIELDS_BY_NAME.put("script", timerScriptField);
		timerScriptField.setHelp("AmiScript to run when timer is executed");
		timerScriptField.setLeftPosPx(AMISCRIPT_FORM_PADDING).setRightPosPx(AMISCRIPT_FORM_PADDING).setBottomPosPx(AMISCRIPT_FORM_PADDING);
		timerScriptField.setWidthPx(400);
		timerScriptField.setHeightPx(AMISCRIPT_FORM_HEIGHT);
		timerScriptField.setTopPosPx(DEFAULT_TOPPOS);

		this.scriptTabs = new TabPortlet(generateConfig());
		this.scriptTabs.setIsCustomizable(false);
		this.scriptTabs.setTabManager(this);
		this.scriptTabs.getTabPortletStyle().setHasMenuAlways(true);
		this.scriptTabs.addChild("Script", scriptGrid);
		this.scriptTabs.addChild("onStartupScript", onStartupScriptGrid);
		GridPortlet grid = new GridPortlet(generateConfig());
		grid.addChild(form, 0, 0);
		grid.addChild(scriptTabs, 0, 1);
		grid.setRowSize(0, OPTION_FORM_HEIGHT);

		this.addChild(grid, 0, 0);
		this.addChild(buttonsFp, 0, 1);

		setRowSize(1, buttonsFp.getButtonPanelHeight());
		if (!isAdd)
			enableEdit(false);

		this.form.addFormPortletListener(this);
		this.scriptForm.addFormPortletListener(this);
		this.onStartupScriptForm.addFormPortletListener(this);
		registerMethods();
		registerProcedures();
	}

	private void registerProcedures() {
		sendQueryToBackend("SHOW FULL PROCEDURES");
	}

	private void registerMethods() {
		sendQueryToBackend("SHOW METHODS WHERE DefinedBy==\"USER\"");
	}

	public AmiCenterManagerEditTimerPortlet(PortletConfig config, String sql, AmiCenterGraphNode_Timer correlationNode) {
		this(config, false);
		this.correlationNode = correlationNode;
		//never allow editing trigger type
		this.timerTypeField.setDisabled(true);
		this.importFromText(sql, new StringBuilder());
		//add form portlet listener
		this.form.addFormPortletListener(this);
		//by default editing is disabled
		enableEdit(false);
	}

	@Override
	public void enableEdit(boolean enable) {
		for (FormPortletField<?> fpf : this.form.getFormFields()) {
			if (fpf != this.enableEditingCheckbox)
				fpf.setDisabled(!enable);
			if (fpf == this.timerVarsField)
				AmiCenterManagerUtils.onFieldDisabled(fpf, !enable);
		}
		this.timerOnStartupScriptField.setDisabled(!enable);
		AmiCenterManagerUtils.onFieldDisabled(this.timerOnStartupScriptField, !enable);
		this.timerScriptField.setDisabled(!enable);
		AmiCenterManagerUtils.onFieldDisabled(this.timerScriptField, !enable);

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
		if (field == this.timerVarsField) {
			parseVars();
		}
		onFieldChanged(field);
	}

	private void parseVars() {
		String[] vars = SH.split(',', this.timerVarsField.getValue());
		JavaExpressionParser jep = new JavaExpressionParser();
		BasicDerivedCellParser cp = new BasicDerivedCellParser(jep);
		AmiWebScriptManager wsm = new AmiWebScriptManager(AmiWebUtils.getService(getManager()), new HashMap<String, AmiScriptClassPluginWrapper>());
		for (String var : vars) {
			try {
				com.f1.utils.string.Node n = jep.parse(var);
				if (n instanceof DeclarationNode) {
					String varName = ((DeclarationNode) n).getVarname();
					String clzzName = ((DeclarationNode) n).getVartype();
					Class<?> clzz = cp.forName(n.getPosition(), wsm.getLayout("").getMethodFactory(), clzzName);
					this.timerScriptField.addVariable(varName, clzz);
					this.timerOnStartupScriptField.addVariable(varName, clzz);
				}
			} catch (Exception e) {
				if (LH.isFine(log))
					LH.fine(log, this.service.getUserName() + ": Problem with Text ", e);
			}
		}
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
		if (SH.is(timerScriptField.getValue()))
			sb.append(" script= ").append(SH.doubleQuote(timerScriptField.getValue()));
		else
			sb.append(" script= ").append(AmiCenterEntityConsts.REQUIRED_FEILD_WARNING);

		if (timerLoggingField.getValue() != AmiCenterEntityConsts.LOGGING_LEVEL_CODE_OFF)
			sb.append(" logging = ").append(SH.doubleQuote(timerLoggingField.getOption(timerLoggingField.getValue()).getName()));
		if (SH.is(timerVarsField.getValue()))
			sb.append(" vars = ").append(SH.doubleQuote(timerVarsField.getValue()));
		if (SH.is(timerOnStartupScriptField.getValue()))
			sb.append(" onStartupScript = ").append(SH.doubleQuote(timerOnStartupScriptField.getValue()));
		if (SH.is(timerTimeoutField.getValue()))
			sb.append(" timeout = ").append(SH.doubleQuote(timerTimeoutField.getValue()));
		if (SH.is(timerLimitField.getValue()))
			sb.append(" limit = ").append(SH.doubleQuote(timerLimitField.getValue()));
		return sb.toString();
	}

	@Override
	public String preparePreUseClause() {
		StringBuilder sb = new StringBuilder("CREATE TIMER ");
		if (SH.is(nameField.getValue()))
			sb.append(nameField.getValue());
		else
			sb.append(AmiCenterEntityConsts.REQUIRED_FEILD_WARNING);
		sb.append(" OFTYPE ").append(timerTypeField.getOption(timerTypeField.getValue()).getName());
		sb.append(" ON ").append(SH.doubleQuote(timerOnField.getValue()));
		if (SH.is(timerPriorityField.getValue()))
			sb.append(" PRIORITY ").append(timerPriorityField.getValue());
		return sb.toString();
	}

	@Override
	public void onUserMenu(TabPortlet tabPortlet, Tab tab, String menuId) {
		if ("export_import_amiscript".equals(menuId)) {
			//				Portlet portlet = this.tabsPortlet.getSelectedTab().getPortlet();
			//				if (portlet instanceof AmiWebChartEditLayerPortlet) {
			//					getManager().showDialog("Export/Import Layer", new AmiWebChartLayerExportPortlet(generateConfig(), layer, (AmiWebChartEditLayerPortlet) portlet));
			//				}
		}

	}

	@Override
	public WebMenu createMenu(TabPortlet tabPortlet, Tab tab) {
		BasicWebMenu r = new BasicWebMenu();
		r.addChild(new BasicWebMenuLink("Export/Import AmiScript", true, "export_import_amiscript"));
		return r;
	}

	@Override
	public void onUserAddTab(TabPortlet tabPortlet) {
		throw new UnsupportedOperationException();

	}

	@Override
	public void onUserRenamedTab(TabPortlet tabPortlet, Tab tab, String newName) {
		throw new UnsupportedOperationException();

	}

	@Override
	public String exportToText() {
		return previewScript();
	}

	@Override
	public void importFromText(String text, StringBuilder sink) {
		AdminNode an = AmiCenterManagerUtils.scriptToAdminNode(text);
		Map<String, String> timerConfig = AmiCenterManagerUtils.parseAdminNode_Timer(an);
		String triggerType = timerConfig.get("timerType");
		this.timerTypeField.setDefaultValue(AmiCenterManagerUtils.centerObjectTypeToCode(AmiCenterGraphNode.TYPE_TIMER, SH.toUpperCase(triggerType)));
		//only type is "AmiScript" for timers, no need to update the template

		for (Entry<String, String> e : timerConfig.entrySet()) {
			String key = e.getKey();
			String value = e.getValue();
			if ("timerName".equals(key)) {
				this.nameField.setValue(value);
				this.nameField.setDefaultValue(value);
			} else if ("timerType".equals(key)) {
				continue;
			} else if ("timerOn".equals(key)) {
				this.timerOnField.setValue(value);
				this.timerOnField.setDefaultValue(value);
			} else if ("timerPriority".equals(key)) {
				this.timerPriorityField.setValue(value);
				this.timerPriorityField.setDefaultValue(value);
			} else if ("timeout".equals(key)) {
				this.timerTimeoutField.setValue(value);
				this.timerTimeoutField.setDefaultValue(value);
			} else if ("limit".equals(key)) {
				this.timerLimitField.setValue(value);
				this.timerLimitField.setDefaultValue(value);
			} else if ("logging".equals(key)) {
				if (SH.is(value)) {
					this.timerLoggingField.setValue(AmiCenterManagerUtils.toLoggingTypeCode(value));
					this.timerLoggingField.setDefaultValue(AmiCenterManagerUtils.toLoggingTypeCode(value));
				}
			} else if ("vars".equals(key)) {
				this.timerVarsField.setValue(value);
				this.timerVarsField.setDefaultValue(value);
				parseVars();
			} else if ("script".equals(key)) {
				this.timerScriptField.setValue(value);
				this.timerScriptField.setDefaultValue(value);
			} else if ("onStartupScript".equals(key)) {
				this.timerOnStartupScriptField.setValue(value);
				this.timerOnStartupScriptField.setDefaultValue(value);
			}
		}
	}

	@Override
	public void onBackendResponse(ResultMessage<Action> result) {
		super.onBackendResponse(result);
		AmiCenterQueryDsResponse response = (AmiCenterQueryDsResponse) result.getAction();
		if (sessionId == -1) {
			this.sessionId = response.getQuerySessionId();
		}
		Action a = result.getRequestMessage().getAction();
		String query = null;
		if (a instanceof AmiCenterQueryDsRequest) {
			AmiCenterQueryDsRequest request = (AmiCenterQueryDsRequest) a;
			query = request.getQuery();
		}
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
								paramClzzs[i] = this.timerScriptField.getCenterMethodFactory().forName(type);
							} catch (ClassNotFoundException e1) {
								LH.warning(log, "Class Not found" + name);
							}
						}
						try {
							Class<?> returnTypeClzz = this.timerScriptField.getCenterMethodFactory().forName(returnType);
							DeclaredMethodFactory dmf = new DeclaredMethodFactory(returnTypeClzz, methodName, paramNames, paramClzzs, (byte) 0);
							this.timerScriptField.addMethodFactory(dmf);
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
								paramClzzs[i] = this.timerScriptField.getCenterMethodFactory().forName(type);
							} catch (ClassNotFoundException e1) {
								LH.warning(log, "Class Not found" + name);
							}
						}
						try {
							Class<?> returnTypeClzz = this.timerScriptField.getCenterMethodFactory().forName(returnType);
							DeclaredMethodFactory dmf = new DeclaredMethodFactory(returnTypeClzz, methodName, paramNames, paramClzzs, (byte) 0);
							timerScriptField.registerProcedure(dmf);
							timerOnStartupScriptField.registerProcedure(dmf);
						} catch (ClassNotFoundException e) {
							e.printStackTrace();
						}

					}
				}

			}
		}
	}

	public static void main(String[] args) {
		JavaExpressionParser jep = new JavaExpressionParser();
		BasicDerivedCellParser cp = new BasicDerivedCellParser(jep);
		MethodNode mn = (MethodNode) cp.getExpressionParser().parse("hello(Integer a, Integer b)");
		DeclarationNode[] s = (DeclarationNode[]) mn.getParamsToArray();
		for (DeclarationNode n : s) {
			String name = n.getVarname();
			String type = n.getVartype();
			try {
				Class<?> typeClzz = OH.forName(type);
			} catch (ClassNotFoundException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}

	@Override
	public String previewEdit() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public boolean ensureCanProceedWithApply() {
		if(SH.isnt(this.nameField.getValue())) {
			AmiCenterManagerUtils.popDialog(service, "The timer name field cannot be empty", "Error Applying Changes");
			return false;
		}
		return true;
		
	}

}
