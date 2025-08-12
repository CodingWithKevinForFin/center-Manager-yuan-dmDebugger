package com.f1.ami.web.centermanager.editors.triggers;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

import com.f1.ami.amicommon.customobjects.AmiScriptClassPluginWrapper;
import com.f1.ami.amicommon.msg.AmiCenterQueryDsRequest;
import com.f1.ami.amicommon.msg.AmiCenterQueryDsResponse;
import com.f1.ami.web.AmiWebFormPortletAmiScriptField;
import com.f1.ami.web.AmiWebScriptManager;
import com.f1.ami.web.AmiWebService;
import com.f1.ami.web.AmiWebUtils;
import com.f1.ami.web.centermanager.editors.AmiCenterManagerAbstractEditCenterObjectPortlet;
import com.f1.ami.web.centermanager.editors.AmiCenterManagerEditTriggerPortlet;
import com.f1.ami.web.centermanager.editors.AmiCenterManagerSubmitEditScriptPortlet;

import com.f1.base.Action;
import com.f1.base.Row;
import com.f1.base.Table;
import com.f1.container.ResultMessage;
import com.f1.suite.web.portal.PortletConfig;
import com.f1.suite.web.portal.impl.GridPortlet;
import com.f1.suite.web.portal.impl.TabPortlet;
import com.f1.suite.web.portal.impl.form.FormPortlet;
import com.f1.suite.web.portal.impl.form.FormPortletButton;
import com.f1.suite.web.portal.impl.form.FormPortletCheckboxField;
import com.f1.suite.web.portal.impl.form.FormPortletField;
import com.f1.suite.web.portal.impl.form.FormPortletListener;
import com.f1.suite.web.portal.impl.form.FormPortletTextField;
import com.f1.utils.CH;
import com.f1.utils.LH;
import com.f1.utils.SH;
import com.f1.utils.string.JavaExpressionParser;
import com.f1.utils.string.Node;
import com.f1.utils.string.node.DeclarationNode;
import com.f1.utils.string.node.MethodNode;
import com.f1.utils.structs.table.derived.BasicDerivedCellParser;
import com.f1.utils.structs.table.derived.DeclaredMethodFactory;

public class AmiCenterManagerTriggerEditor_AmiscirptTrigger extends AmiCenterManagerAbstractTriggerEditor implements FormPortletListener {
	public static final int DEFAULT_ROWHEIGHT = AmiCenterManagerAbstractEditCenterObjectPortlet.DEFAULT_ROWHEIGHT;
	public static final int DEFAULT_LEFTPOS = AmiCenterManagerAbstractEditCenterObjectPortlet.DEFAULT_LEFTPOS; //164
	public static final int DEFAULT_Y_SPACING = AmiCenterManagerAbstractEditCenterObjectPortlet.DEFAULT_Y_SPACING;
	public static final int DEFAULT_X_SPACING = AmiCenterManagerAbstractEditCenterObjectPortlet.DEFAULT_X_SPACING;
	public static final int DEFAULT_TOPPOS = DEFAULT_Y_SPACING;

	public static final int OPTION_FORM_HEIGHT = 80;
	public static final int AMISCRIPT_FORM_HEIGHT = 600;
	public static final int AMISCRIPT_FORM_PADDING = 0;
	final private AmiWebService service;
	private static final Logger log = LH.get();
	final private TabPortlet scriptTabs;

	//option fields
	final private FormPortletCheckboxField canMutateRowField;
	final private FormPortletTextField rowVarField;
	final private AmiWebFormPortletAmiScriptField varsField;
	//script fields
	final private GridPortlet onInsertingScriptGrid;
	final private FormPortlet onInsertingScriptForm;
	final private AmiWebFormPortletAmiScriptField onInsertingScriptField;

	final private GridPortlet onUpdatingScriptGrid;
	final private FormPortlet onUpdatingScriptForm;
	final private AmiWebFormPortletAmiScriptField onUpdatingScriptField;

	final private GridPortlet onDeletingScriptGrid;
	final private FormPortlet onDeletingScriptForm;
	final private AmiWebFormPortletAmiScriptField onDeletingScriptField;

	final private GridPortlet onInsertedScriptGrid;
	final private FormPortlet onInsertedScriptForm;
	final private AmiWebFormPortletAmiScriptField onInsertedScriptField;

	final private GridPortlet onUpdatedScriptGrid;
	final private FormPortlet onUpdatedScriptForm;
	final private AmiWebFormPortletAmiScriptField onUpdatedScriptField;

	final private GridPortlet onStartupScriptGrid;
	final private FormPortlet onStartupScriptForm;
	final private AmiWebFormPortletAmiScriptField onStartupScriptField;

	public AmiCenterManagerTriggerEditor_AmiscirptTrigger(PortletConfig config, AmiCenterManagerEditTriggerPortlet mainEditor) {
		super(config, mainEditor);
		service = AmiWebUtils.getService(getManager());
		canMutateRowField = form.addField(new FormPortletCheckboxField("canMutateRow"));
		canMutateRowField.setHelp(" If true, then any values of the row changed inside the onInsertingScript will reflect back on the row to be inserted." + "<br>"
				+ "For onUpdatingScript, any changes to the new_varname values will reflect on the row to be updated." + "<br>"
				+ "Note, this only applies to the onInsertingScript and onUpdatingScript options, has no effect on onInsertedScript, onUpdatedScript, and onDeletingScript. ");
		canMutateRowField.setLeftPosPx(DEFAULT_LEFTPOS + 63).setWidthPx(40).setTopPosPx(DEFAULT_TOPPOS + 10);
		canMutateRowField.setName("canMutateRow");

		rowVarField = form.addField(new FormPortletTextField("rowVar"));
		rowVarField.setLeftPosPx(DEFAULT_LEFTPOS + 250).setWidth(180).setHeightPx(DEFAULT_ROWHEIGHT).setTopPosPx(DEFAULT_TOPPOS + 5);
		rowVarField.setHelp("a placeholder (can be any custom variable name) that contains the map that reflects the row change in the table (either insert, update or delete)."
				+ "<br>" + " Note that rowVar is a read-only map and the available methods include:" + "<br>"
				+ "<b><i style=\"color:blue\"> boolean containsValue(), boolean containsKey().</i></b>" + "<br>"
				+ " For onUpdatingScript, you must add the <b><i style=\"color:blue\">new_</i></b> or <b><i style=\"color:blue\">old_</i></b> prefix to the rowVar to use it. ");
		rowVarField.setName("rowVar");

		varsField = form.addField(new AmiWebFormPortletAmiScriptField("vars", getManager(), ""));
		varsField.setLeftPosPx(DEFAULT_LEFTPOS).setWidth(700).setTopPosPx(DEFAULT_TOPPOS + 40).setHeight(20);
		varsField.setName("vars");
		varsField.setHelp("Variables shared by the trigger, a comma delimited list of type varname");

		onInsertingScriptGrid = new GridPortlet(generateConfig());
		onInsertingScriptForm = new FormPortlet(generateConfig());
		onInsertingScriptGrid.addChild(onInsertingScriptForm);
		onInsertingScriptField = onInsertingScriptForm
				.addField(new AmiWebFormPortletAmiScriptField("", getManager(), AmiWebFormPortletAmiScriptField.LANGUAGE_SCOPE_CENTER_SCRIPT));
		onInsertingScriptField.setLeftPosPx(AMISCRIPT_FORM_PADDING).setRightPosPx(AMISCRIPT_FORM_PADDING).setBottomPosPx(AMISCRIPT_FORM_PADDING);
		onInsertingScriptField.setWidthPx(400).setTopPosPx(DEFAULT_TOPPOS);
		onInsertingScriptField.setHeightPx(AMISCRIPT_FORM_HEIGHT);

		onUpdatingScriptGrid = new GridPortlet(generateConfig());
		onUpdatingScriptForm = new FormPortlet(generateConfig());
		onUpdatingScriptGrid.addChild(onUpdatingScriptForm);
		onUpdatingScriptField = onUpdatingScriptForm.addField(new AmiWebFormPortletAmiScriptField("", getManager(), AmiWebFormPortletAmiScriptField.LANGUAGE_SCOPE_CENTER_SCRIPT));
		onUpdatingScriptField.setLeftPosPx(AMISCRIPT_FORM_PADDING).setRightPosPx(AMISCRIPT_FORM_PADDING).setBottomPosPx(AMISCRIPT_FORM_PADDING);
		onUpdatingScriptField.setWidthPx(400).setTopPosPx(DEFAULT_TOPPOS);
		onUpdatingScriptField.setHeightPx(AMISCRIPT_FORM_HEIGHT);

		onDeletingScriptGrid = new GridPortlet(generateConfig());
		onDeletingScriptForm = new FormPortlet(generateConfig());
		onDeletingScriptGrid.addChild(onDeletingScriptForm);
		onDeletingScriptField = onDeletingScriptForm.addField(new AmiWebFormPortletAmiScriptField("", getManager(), AmiWebFormPortletAmiScriptField.LANGUAGE_SCOPE_CENTER_SCRIPT));
		onDeletingScriptField.setLeftPosPx(AMISCRIPT_FORM_PADDING).setRightPosPx(AMISCRIPT_FORM_PADDING).setBottomPosPx(AMISCRIPT_FORM_PADDING);
		onDeletingScriptField.setWidthPx(400).setTopPosPx(DEFAULT_TOPPOS);
		onDeletingScriptField.setHeightPx(AMISCRIPT_FORM_HEIGHT);

		onInsertedScriptGrid = new GridPortlet(generateConfig());
		onInsertedScriptForm = new FormPortlet(generateConfig());
		onInsertedScriptGrid.addChild(onInsertedScriptForm);
		onInsertedScriptField = onInsertedScriptForm.addField(new AmiWebFormPortletAmiScriptField("", getManager(), AmiWebFormPortletAmiScriptField.LANGUAGE_SCOPE_CENTER_SCRIPT));
		onInsertedScriptField.setLeftPosPx(AMISCRIPT_FORM_PADDING).setRightPosPx(AMISCRIPT_FORM_PADDING).setBottomPosPx(AMISCRIPT_FORM_PADDING);
		onInsertedScriptField.setWidthPx(400).setTopPosPx(DEFAULT_TOPPOS);
		onInsertedScriptField.setHeightPx(AMISCRIPT_FORM_HEIGHT);

		onUpdatedScriptGrid = new GridPortlet(generateConfig());
		onUpdatedScriptForm = new FormPortlet(generateConfig());
		onUpdatedScriptGrid.addChild(onUpdatedScriptForm);
		onUpdatedScriptField = onUpdatedScriptForm.addField(new AmiWebFormPortletAmiScriptField("", getManager(), AmiWebFormPortletAmiScriptField.LANGUAGE_SCOPE_CENTER_SCRIPT));
		onUpdatedScriptField.setLeftPosPx(AMISCRIPT_FORM_PADDING).setRightPosPx(AMISCRIPT_FORM_PADDING).setBottomPosPx(AMISCRIPT_FORM_PADDING);
		onUpdatedScriptField.setWidthPx(400).setTopPosPx(DEFAULT_TOPPOS);
		onUpdatedScriptField.setHeightPx(AMISCRIPT_FORM_HEIGHT);

		onStartupScriptGrid = new GridPortlet(generateConfig());
		onStartupScriptForm = new FormPortlet(generateConfig());
		onStartupScriptGrid.addChild(onStartupScriptForm);
		onStartupScriptField = onStartupScriptForm.addField(new AmiWebFormPortletAmiScriptField("", getManager(), AmiWebFormPortletAmiScriptField.LANGUAGE_SCOPE_CENTER_SCRIPT));
		onStartupScriptField.setLeftPosPx(AMISCRIPT_FORM_PADDING).setRightPosPx(AMISCRIPT_FORM_PADDING).setBottomPosPx(AMISCRIPT_FORM_PADDING);
		onStartupScriptField.setWidthPx(400).setTopPosPx(DEFAULT_TOPPOS);
		onStartupScriptField.setHeightPx(AMISCRIPT_FORM_HEIGHT);

		this.scriptTabs = new TabPortlet(generateConfig());
		this.scriptTabs.setIsCustomizable(false);
		this.scriptTabs.addChild("onInsertingScript", onInsertingScriptGrid);
		this.scriptTabs.addChild("onUpdatingScript", onUpdatingScriptGrid);
		this.scriptTabs.addChild("onDeletingScript", onDeletingScriptGrid);
		this.scriptTabs.addChild("onInsertedScript", onInsertedScriptGrid);
		this.scriptTabs.addChild("onUpdatedScript", onUpdatedScriptGrid);
		this.scriptTabs.addChild("onStartupScript", onStartupScriptGrid);

		form.addFormPortletListener(this);
		onInsertedScriptForm.addFormPortletListener(this);
		onInsertingScriptForm.addFormPortletListener(this);
		onDeletingScriptForm.addFormPortletListener(this);
		onUpdatingScriptForm.addFormPortletListener(this);
		onUpdatedScriptForm.addFormPortletListener(this);
		onStartupScriptForm.addFormPortletListener(this);

		registerMethods();
		registerProcedures();

		addChild(form, 0, 0);
		addChild(scriptTabs, 0, 1);
		setRowSize(0, OPTION_FORM_HEIGHT);

	}

	private void registerProcedures() {
		sendQueryToBackend("SHOW FULL PROCEDURES");
	}

	@Override
	public String getKeyValuePairs() {
		StringBuilder sb = new StringBuilder();
		if (SH.is(varsField.getValue()))
			sb.append(" vars = ").append(SH.doubleQuote(varsField.getValue()));

		if (SH.is(onStartupScriptField.getValue()))
			sb.append(" onStartupScript = ").append(SH.doubleQuote(onStartupScriptField.getValue()));

		if (SH.is(onInsertingScriptField.getValue()))
			sb.append(" onInsertingScript = ").append(SH.doubleQuote(onInsertingScriptField.getValue()));

		if (SH.is(onInsertedScriptField.getValue()))
			sb.append(" onInsertedScript = ").append(SH.doubleQuote(onInsertedScriptField.getValue()));

		if (SH.is(onUpdatingScriptField.getValue()))
			sb.append(" onUpdatingScript = ").append(SH.doubleQuote(onUpdatingScriptField.getValue()));

		if (SH.is(onUpdatedScriptField.getValue()))
			sb.append(" onUpdatedScript = ").append(SH.doubleQuote(onUpdatedScriptField.getValue()));

		if (SH.is(onDeletingScriptField.getValue()))
			sb.append(" onDeletingScript = ").append(SH.doubleQuote(onDeletingScriptField.getValue()));

		if (canMutateRowField.getBooleanValue())
			sb.append(" canMutateRow = ").append(SH.doubleQuote("true"));

		if (SH.is(rowVarField.getValue()))
			sb.append(" rowVar = ").append(SH.doubleQuote(rowVarField.getValue()));
		return sb.toString();
	}

	@Override
	public FormPortletField<?> getFieldByName(String name) {
		if ("vars".equals(name))
			return this.varsField;
		if ("rowVar".equals(name))
			return this.rowVarField;
		if ("canMutateRow".equals(name))
			return this.canMutateRowField;
		if ("onInsertingScript".equals(name))
			return this.onInsertingScriptField;
		if ("onInsertedScript".equals(name))
			return this.onInsertedScriptField;
		if ("onUpdatingScript".equals(name))
			return this.onUpdatingScriptField;
		if ("onUpdatedScript".equals(name))
			return this.onUpdatedScriptField;
		if ("onDeletingScript".equals(name))
			return this.onDeletingScriptField;
		throw new NullPointerException("No such name:" + name);

	}

	public void parseVars() {
		String[] vars = SH.split(',', this.varsField.getValue());
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
					this.onInsertingScriptField.addVariable(varName, clzz);
					this.onInsertedScriptField.addVariable(varName, clzz);
					this.onDeletingScriptField.addVariable(varName, clzz);
					this.onUpdatingScriptField.addVariable(varName, clzz);
					this.onUpdatedScriptField.addVariable(varName, clzz);
					this.onStartupScriptField.addVariable(varName, clzz);
				}
			} catch (Exception e) {
				if (LH.isFine(log))
					LH.fine(log, this.service.getUserName() + ": Problem with Text ", e);
			}
		}
	}

	@Override
	public void onButtonPressed(FormPortlet portlet, FormPortletButton button) {
		// TODO Auto-generated method stub

	}

	@Override
	public void onFieldValueChanged(FormPortlet portlet, FormPortletField<?> field, Map<String, String> attributes) {
		if (field == this.varsField) {
			parseVars();
		}

	}

	@Override
	public void onSpecialKeyPressed(FormPortlet formPortlet, FormPortletField<?> field, int keycode, int mask, int cursorPosition) {
		if (field instanceof AmiWebFormPortletAmiScriptField)
			((AmiWebFormPortletAmiScriptField) field).onSpecialKeyPressed(formPortlet, field, keycode, mask, cursorPosition);

	}

	//The abilities to query the backend
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
				if (query.startsWith("SHOW FULL METHODS")) {
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
								//TODO: add all other script fields
								paramClzzs[i] = this.onInsertedScriptField.getCenterMethodFactory().forName(type);
							} catch (ClassNotFoundException e1) {
								LH.warning(log, "Class Not found" + name);
							}
						}
						try {
							Class<?> returnTypeClzz = this.onInsertedScriptField.getCenterMethodFactory().forName(returnType);
							DeclaredMethodFactory dmf = new DeclaredMethodFactory(returnTypeClzz, methodName, paramNames, paramClzzs, (byte) 0);
							onInsertedScriptField.addMethodFactory(dmf);
							onInsertingScriptField.addMethodFactory(dmf);
							onDeletingScriptField.addMethodFactory(dmf);
							onUpdatingScriptField.addMethodFactory(dmf);
							onUpdatedScriptField.addMethodFactory(dmf);
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
								paramClzzs[i] = this.onInsertedScriptField.getCenterMethodFactory().forName(type);
							} catch (ClassNotFoundException e1) {
								LH.warning(log, "Class Not found" + name);
							}
						}
						try {
							Class<?> returnTypeClzz = this.onInsertedScriptField.getCenterMethodFactory().forName(returnType);
							DeclaredMethodFactory dmf = new DeclaredMethodFactory(returnTypeClzz, methodName, paramNames, paramClzzs, (byte) 0);
							onInsertedScriptField.registerProcedure(dmf);
							onInsertingScriptField.registerProcedure(dmf);
							onDeletingScriptField.registerProcedure(dmf);
							onUpdatingScriptField.registerProcedure(dmf);
							onUpdatedScriptField.registerProcedure(dmf);
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
		//		request.setQuerySessionId(this.sessionId);
		service.sendRequestToBackend(this, request);
	}

	private void registerMethods() {
		sendQueryToBackend("SHOW FULL METHODS WHERE DefinedBy==\"USER\"");
	}

	@Override
	public void enableEdit(boolean enable) {
		canMutateRowField.setDisabled(!enable);
		rowVarField.setDisabled(!enable);
		varsField.setDisabled(!enable);
		onInsertedScriptField.setDisabled(!enable);
		onInsertingScriptField.setDisabled(!enable);
		onUpdatedScriptField.setDisabled(!enable);
		onUpdatingScriptField.setDisabled(!enable);
		onStartupScriptField.setDisabled(!enable);
		onDeletingScriptField.setDisabled(!enable);
	}

	@Override
	public Set<? extends FormPortlet> getSmartEditors() {
		return CH.s(this.onInsertedScriptForm, this.onInsertingScriptForm, this.onUpdatedScriptForm, this.onUpdatingScriptForm, this.onDeletingScriptForm,
				this.onStartupScriptForm);

	}

}
