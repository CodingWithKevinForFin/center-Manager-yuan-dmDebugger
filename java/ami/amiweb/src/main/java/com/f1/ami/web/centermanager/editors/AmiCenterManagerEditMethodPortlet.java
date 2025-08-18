package com.f1.ami.web.centermanager.editors;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.f1.ami.amicommon.msg.AmiCenterQueryDsRequest;
import com.f1.ami.amicommon.msg.AmiCenterQueryDsResponse;
import com.f1.ami.amiscript.AmiDebugManager;
import com.f1.ami.amiscript.AmiDebugMessage;
import com.f1.ami.web.AmiWebFormPortletAmiScriptField;
import com.f1.ami.web.AmiWebMethodUsagesTreePortlet;
import com.f1.ami.web.AmiWebService;
import com.f1.ami.web.AmiWebUtils;
import com.f1.ami.web.amiscript.AmiWebAmiScriptDerivedCellParser;
import com.f1.ami.web.amiscript.AmiWebCalcTypesStack;
import com.f1.ami.web.centermanager.AmiCenterManagerUtils;
import com.f1.ami.web.centermanager.autocomplete.AmiCenterManagerImdbScriptManager;
import com.f1.ami.web.centermanager.editors.AmiCenterManagerSubmitEditScriptPortlet;
import com.f1.base.Action;
import com.f1.base.CalcTypes;
import com.f1.base.Row;
import com.f1.base.Table;
import com.f1.container.ContainerTools;
import com.f1.container.ResultMessage;
import com.f1.suite.web.fastwebcolumns.FastWebColumns;
import com.f1.suite.web.menu.WebMenu;
import com.f1.suite.web.portal.PortletConfig;
import com.f1.suite.web.portal.impl.DividerPortlet;
import com.f1.suite.web.portal.impl.FastTablePortlet;
import com.f1.suite.web.portal.impl.form.FormPortlet;
import com.f1.suite.web.portal.impl.form.FormPortletField;
import com.f1.suite.web.portal.impl.form.FormPortletTitleField;
import com.f1.suite.web.table.WebColumn;
import com.f1.suite.web.table.WebContextMenuFactory;
import com.f1.suite.web.table.WebContextMenuListener;
import com.f1.suite.web.table.WebTable;
import com.f1.suite.web.table.fast.FastWebTable;
import com.f1.utils.IOH;
import com.f1.utils.LH;
import com.f1.utils.OH;
import com.f1.utils.SH;
import com.f1.utils.sql.SqlDerivedCellParser;
import com.f1.utils.sql.SqlProcessor;
import com.f1.utils.string.Node;
import com.f1.utils.string.SqlExpressionParser;
import com.f1.utils.string.node.DeclarationNode;
import com.f1.utils.string.node.MethodDeclarationNode;
import com.f1.utils.string.sqlnode.AdminNode;
import com.f1.utils.structs.Tuple2;
import com.f1.utils.structs.table.BasicTable;
import com.f1.utils.structs.table.derived.BasicDerivedCellParser;
import com.f1.utils.structs.table.derived.BasicExternFactoryManager;
import com.f1.utils.structs.table.derived.BasicMethodFactory;
import com.f1.utils.structs.table.derived.DeclaredMethodFactory;
import com.f1.utils.structs.table.derived.DerivedCellCalculator;
import com.f1.utils.structs.table.derived.DerivedCellCalculatorBlock;
import com.f1.utils.structs.table.derived.DerivedCellCalculatorExpression;
import com.f1.utils.structs.table.derived.MethodFactory;
import com.f1.utils.structs.table.derived.MethodFactoryManager;
import com.f1.utils.structs.table.derived.ParamsDefinition;
import com.f1.utils.structs.table.stack.CalcTypesStack;
import com.f1.utils.structs.table.stack.CalcTypesTuple2;
import com.f1.utils.structs.table.stack.ChildCalcTypesStack;
import com.f1.utils.structs.table.stack.EmptyCalcFrame;
import com.f1.utils.structs.table.stack.EmptyCalcTypes;

public class AmiCenterManagerEditMethodPortlet extends AmiCenterManagerAbstractEditCenterObjectPortlet implements WebContextMenuFactory, WebContextMenuListener {
	private FastTablePortlet table;
	private BasicTable methodsTable;
	private AmiWebService service;
	private FormPortlet bodyForm;
	private AmiWebMethodUsagesTreePortlet usages;
	private AmiWebFormPortletAmiScriptField bodyField;
	private DividerPortlet outerDiv;
	private DividerPortlet leftDiv;
	private FormPortletTitleField titleField;

	//parser
	final private SqlProcessor sqlProcessor;
	final private SqlDerivedCellParser amiScriptParser;
	final private SqlExpressionParser expressionParser;
	final private BasicExternFactoryManager externFactory;

	//test
	List<MethodFactory> sink = new ArrayList<MethodFactory>();

	private static final Comparator<MethodFactory> FACTORY_COMPARATOR = new Comparator<MethodFactory>() {

		@Override
		public int compare(MethodFactory o1, MethodFactory o2) {
			ParamsDefinition d1 = o1.getDefinition();
			ParamsDefinition d2 = o2.getDefinition();
			int r = OH.compare(d1.getMethodName(), d2.getMethodName());
			if (r != 0)
				return r;
			int min = Math.min(d1.getParamsCount(), d2.getParamsCount());
			for (int i = 0; i < min; i++) {
				r = OH.compare(d1.getParamName(i), d2.getParamName(i));
				if (r != 0)
					return r;
			}
			return OH.compare(d1.getParamsCount(), d2.getParamsCount());
		}
	};

	final private AmiCenterManagerImdbScriptManager scriptManager;

	public AmiCenterManagerEditMethodPortlet(PortletConfig config, boolean isAdd) {
		super(config, isAdd);

		this.methodsTable = new BasicTable();
		this.service = AmiWebUtils.getService(getManager());

		ContainerTools tools = service.getPortletManager().getTools();
		this.externFactory = new BasicExternFactoryManager();
		this.sqlProcessor = new SqlProcessor();
		this.expressionParser = sqlProcessor.getExpressionParser();
		this.amiScriptParser = new AmiWebAmiScriptDerivedCellParser(this.sqlProcessor.getExpressionParser(), this.sqlProcessor, tools, externFactory, "", true);

		methodsTable.addColumn(String.class, "Line");
		methodsTable.addColumn(String.class, "Start");
		methodsTable.addColumn(String.class, "End");
		methodsTable.addColumn(String.class, "Name");
		methodsTable.addColumn(String.class, "Body");
		methodsTable.addColumn(String.class, "Params");
		this.table = new FastTablePortlet(generateConfig(), methodsTable, "Methods");
		this.table.getTable().addMenuListener(this);
		this.table.getTable().setMenuFactory(this);
		this.table.getTable().addColumn(true, "Name", "Name", service.getDesktop().getService().getFormatterManager().getBasicFormatter()).setWidth(230);
		this.table.getTable().addColumn(true, "Line", "Line", service.getDesktop().getService().getFormatterManager().getBasicFormatter()).setWidth(50);
		this.table.getTable().addColumn(true, "Body", "Body", service.getDesktop().getService().getFormatterManager().getBasicFormatter()).setWidth(100);
		this.table.addOption(FastTablePortlet.OPTION_QUICK_COLUMN_FILTER_HIDDEN, false);
		this.scriptManager = new AmiCenterManagerImdbScriptManager(service);
		sendQueryToBackend("SHOW FULL METHODS WHERE DefinedBy == \"USER\" ORDER BY Definition;");

		this.bodyForm = new FormPortlet(generateConfig());
		//		this.errorPortlet = new HtmlPortlet(generateConfig());
		//		this.tabsPortlet = new TabPortlet(generateConfig());
		//		this.tabsPortlet.setIsCustomizable(false);
		//		this.debuggerPortlet = new AmiWebDebugPortlet(generateConfig());
		//		this.tabsPortlet.addChild("Errors", this.errorPortlet);
		//		this.tabsPortlet.addChild("Debugger", this.debuggerPortlet);
		//		this.div = new DividerPortlet(generateConfig(), false, this.bodyForm, this.tabsPortlet);
		//		this.div.setOffset(1);
		this.usages = new AmiWebMethodUsagesTreePortlet(generateConfig(), service, "");
		this.leftDiv = new DividerPortlet(generateConfig(), false);
		this.leftDiv.setOffset(.75);
		this.leftDiv.addChild(this.table);
		this.leftDiv.addChild(this.usages);
		this.outerDiv = new DividerPortlet(generateConfig(), true);
		this.outerDiv.addChild(this.leftDiv);
		this.outerDiv.addChild(this.bodyForm);
		this.outerDiv.setOffsetPx(350);
		this.addChild(outerDiv, 0, 0);
		this.bodyForm.getFormPortletStyle().setLabelsWidth(10);
		this.titleField = bodyForm.addField(new FormPortletTitleField("Ami Script:" + (false ? " (readonly layout)" : "")));
		this.bodyField = bodyForm.addField(new AmiWebFormPortletAmiScriptField("", this.service.getPortletManager(), AmiWebFormPortletAmiScriptField.LANGUAGE_SCOPE_CENTER_SCRIPT));
		//		this.bodyField.setValue(SH.is(origAmiScriptMethods) ? origAmiScriptMethods : "{\n}");
		this.bodyField.focus();
		//		this.bodyField.addVariable("layout", AmiWebLayoutFile.class);
		//		this.bodyField.addVariable("session", AmiWebService.class);
		//		this.bodyField.addVariable("this", AmiWebService.class);
		this.setSuggestedSize(1000, getManager().getRoot().getHeight() - 50);
		this.bodyField.setHeight(FormPortletField.SIZE_STRETCH);
		this.bodyForm.addFormPortletListener(this);
		this.bodyForm.setMenuFactory(this);
		this.bodyForm.addMenuListener(this);
		this.bodyField.setDisabled(false);

		executeSqlFile();

	}

	private String getDeclaredMethodsScript() {
		StringBuilder sql = new StringBuilder();
		MethodFactoryManager mf = scriptManager.getManagedMethodFactory();
		List<MethodFactory> sink = new ArrayList<MethodFactory>();
		mf.getAllMethodFactories(sink);
		Collections.sort(sink, FACTORY_COMPARATOR);
		sql.append("/*CUSTOM DB METHODS*/").append(SH.NEWLINE).append(SH.NEWLINE);
		if (sink.size() > 0) {
			sql.append("CREATE METHOD {");
			for (MethodFactory i : sink) {
				sql.append(SH.NEWLINE);
				DeclaredMethodFactory dmf = (DeclaredMethodFactory) i;
				dmf.getText(this.scriptManager.getMethodFactory(), sql);
				sql.append(SH.NEWLINE);
			}
			sql.append("}");
		}
		sql.append(SH.NEWLINE);
		return sql.toString();
	}

	private void buildMethodsTable() {
		String script = getDeclaredMethodsScript();
		MethodFactoryManager mfm = scriptManager.getMethodFactory();
		this.methodsTable.clear();
		for (DeclaredMethodFactory dmf : scriptManager.getManagedMethodFactories()) {
			ParamsDefinition def = dmf.getDefinition();
			int pos = dmf.getBodyStart();
			Tuple2<Integer, Integer> lp = SH.getLinePosition(script, pos);
			methodsTable.getRows().addRow(lp.getA(), dmf.getBodyStart(), dmf.getBodyEnd(), SH.afterFirst(def.toString(mfm), ' '), dmf.getText(mfm), dmf.getDefinition());
		}
	}

	public void executeSqlFile() {
		File file = new File("data/managed_schema.amisql");
		try {
			LH.info(log, "Processing Schema script from the web: ", IOH.getFullPathAndCheckSum(file));
			String data = IOH.readText(file);
			String code = "Integer alterShockDisplayTables(Map tableConfigs){\r\n" + "   for (String tbl : tableConfigs.getKeys()) {\r\n"
					+ "        Map conf = tableConfigs.get(tbl);\r\n" + "        String alterCol = (String)(conf.get(\"alterColumnDefinition\"));\r\n"
					+ "        if (strIsnt(alterCol)) continue;\r\n" + "        logInfo(\"ALTERING TABLE ${tbl} WITH ALTER COLUMN ${alterCol}\");\r\n"
					+ "        ALTER TABLE ${tbl} ${alterCol};\r\n" + "    }\r\n" + "    return 1;\r\n" + "};\r\n" + "\r\n" + "  Integer checkForInstrumentChanges(){\r\n"
					+ "    List tables = new List();\r\n" + "    tables.add(\"riskInstrumentInputAssumptionsAQON\");\r\n"
					+ "    tables.add(\"riskInstrumentInputAssumptionsAQOFF\");\r\n" + "\r\n" + "    Table inst = USE ds=\"AMI\" EXECUTE SELECT instruments.baseAsset AS symbol\r\n"
					+ "        FROM instruments LEFT ONLY JOIN ${tables.get(0)}\r\n" + "        ON instruments.baseAsset == ${tables.get(0)}.I\r\n"
					+ "        WHERE instruments.instrumentType==\"PERPETUAL_FUTURE\";\r\n" + "    List rows = inst.getRows();\r\n"
					+ "    if (null == rows || 0 == rows.size()) return 0;\r\n" + "    String values = \"VALUES\";\r\n" + "    for (Row row : rows) {\r\n"
					+ "        String sym = row.getValue(\"symbol\");\r\n" + "        values += \"(\\\"${sym}\\\",\\\"${sym}\\\", 0.0, 0.0),\";\r\n" + "    }\r\n"
					+ "    values = values.beforeLast(\",\", false) + \";\";\r\n" + "    if (null == values) return;\r\n" + "\r\n" + "    for (String targetTable : tables) {\r\n"
					+ "        String stmnt = \"INSERT INTO ${targetTable} (I, symbol, thirtyADQNotional, lspCapacityNotional) \" + values;\r\n"
					+ "        USE ds=\"AMI\" EXECUTE ${stmnt};\r\n" + "    }\r\n" + "    return 1;\r\n" + "};\r\n" + "\r\n"
					+ "  Table fetchTableauHyper(String tableauServerDS,String hyperDS,String shellDS,String tableauSiteName,String tableauDataSourceName){\r\n" + " \r\n"
					+ "  Map credentials = new Map();\r\n" + "  Map site = new Map();\r\n" + "  Map params = new Map();\r\n" + " \r\n"
					+ "  String user = use ds=AMI EXECUTE SELECT US FROM __DATASOURCE WHERE NM == \"${tableauServerDS}\";\r\n"
					+ "  String password = use ds=AMI EXECUTE SELECT strDecrypt(Password) FROM __DATASOURCE WHERE NM == \"${tableauServerDS}\";\r\n" + " \r\n"
					+ "  site.put(\"contentUrl\", tableauSiteName);\r\n" + "  credentials.put(\"name\", user);\r\n" + "  credentials.put(\"password\", password);\r\n"
					+ "  credentials.put(\"site\", site);\r\n" + "  params.put(\"credentials\", credentials);\r\n" + " \r\n"
					+ "  CREATE TABLE signin,headers AS USE ds=\"${tableauServerDS}\" _method=\"POST\" _urlExtension=\"/auth/signin\" _header_Accept=\"application/json\" _params=\"${params.toJson()}\" _delim=\"||||\" _returnHeaders=\"true\" EXECUTE SELECT * FROM signin;\r\n"
					+ " \r\n" + "  String json = select credentials from signin limit 1;\r\n" + "  String siteId = jsonExtract(json, \"site.id\");\r\n"
					+ "  String token = jsonExtract(json, \"token\");\r\n" + " \r\n" + "  Map headers = new Map();\r\n" + "  headers.put(\"X-Tableau-Auth\", token);\r\n"
					+ "  headers.put(\"Accept\", \"application/json\");\r\n" + " \r\n"
					+ "  CREATE TABLE getDataSources,headers AS USE ds=\"${tableauServerDS}\" _method=\"GET\" _urlExtension=\"/sites/${siteId}/datasources\" _header_Accept=\"application/json\" _headers=\"${headers.toJson()}\" _param_pageSize=\"1000\" _delim=\"||||\" _returnHeaders=\"true\" EXECUTE SELECT * FROM getDataSources;\r\n"
					+ " \r\n" + "  String dataSourcesJson = select datasources from getDataSources;\r\n" + " \r\n" + "  Map dataSourceMap = parseJson(dataSourcesJson);\r\n"
					+ " \r\n" + "  List dataSourceList = dataSourceMap.get(\"datasource\");\r\n" + " \r\n" + "  String dataSourceId = \"\";\r\n"
					+ "  for(int i = 0; i < dataSourceList.size(); i++) {\r\n" + "    Map dataSource = dataSourceList.get(i);\r\n"
					+ "    if(dataSource.get(\"name\") == tableauDataSourceName) {\r\n" + "      dataSourceId = dataSource.get(\"id\");\r\n" + "    }\r\n" + "  }\r\n" + " \r\n"
					+ "  String tableauServer = use ds=AMI EXECUTE SELECT UR FROM __DATASOURCE WHERE NM == \"${tableauServerDS}\";\r\n" + " \r\n"
					+ "  String curlCommand = \"curl -v -H 'X-Tableau-Auth: ${token}' -o ${tableauDataSourceName}.tdsx ${tableauServer}/sites/${siteId}/datasources/${dataSourceId}/content?includeExtract=true\";\r\n"
					+ " \r\n" + "  String unzipCommand = \"unzip -o -d ${tableauDataSourceName} ${tableauDataSourceName}.tdsx\";\r\n" + " \r\n"
					+ "  CREATE TABLE stdoutCurl,stderrCurl,exitCodeCurl AS USE ds=\"${shellDS}\" _cmd=\"${curlCommand}\" EXECUTE SELECT * FROM cmd;\r\n" + " \r\n"
					+ "  CREATE TABLE stdoutZip,stderrZip,exitCodeZip AS USE ds=\"${shellDS}\" _cmd=\"${unzipCommand}\" EXECUTE SELECT * FROM cmd;\r\n" + " \r\n"
					+ "  List zipOutput = select line from stdoutZip;\r\n" + " \r\n" + "  List hyperFiles = new List();\r\n" + " \r\n"
					+ "  for(int i = 0; i < zipOutput.size(); i++) {\r\n" + "    String line = zipOutput.get(i);\r\n" + "    if(line =~ \".hyper\") {\r\n"
					+ "      List lineTokens = line.split(\" \");\r\n" + "      for(int j = 0; j < lineTokens.size(); j++) {\r\n"
					+ "        if(lineTokens.get(j) =~ \".hyper$\") {\r\n" + "          hyperFiles.add(lineTokens.get(j));\r\n" + "        }\r\n" + "      }\r\n" + "    }\r\n"
					+ " \r\n" + "  }\r\n" + "  String dir = use ds=AMI EXECUTE SELECT UR FROM __DATASOURCE WHERE NM == \"${shellDS}\";\r\n" + " \r\n"
					+ "  String fullPathToHyper = dir + \"/\" + hyperFiles.get(0);\r\n" + " \r\n"
					+ "  CREATE TABLE HyperOutput as use ds=${hyperDS} _file=\"${fullPathToHyper}\" EXECUTE SELECT * FROM \"Extract\".\"Extract\";\r\n" + " \r\n"
					+ "  Table returnTable = select * from HyperOutput;\r\n" + " \r\n" + "  return returnTable;\r\n" + " \r\n" + "}\r\n" + ";\r\n" + "\r\n"
					+ "  Integer foo(Integer a){return 0;};\r\n" + "\r\n" + "  Integer hello(Integer a,Integer b){return a+b;};\r\n" + "\r\n"
					+ "  Integer hello2(Integer a,Integer b){return a+b;};\r\n" + "\r\n" + "  Integer hello3(Integer a,Integer b){return a+b;};\r\n" + "\r\n"
					+ "  Integer initAndSetupConfigs(){\r\n" + "    syncScenarios();\r\n" + "    syncInitRiskConfigurations();\r\n" + "    syncActiveInstrumentAssumptions();\r\n"
					+ "    checkForInstrumentChanges();\r\n" + "    return 1;\r\n" + "};\r\n" + "\r\n" + "  boolean isScenarioDisabled(Map scenario,String callerName){\r\n"
					+ "    String name = scenario.get(\"scenarioName\");\r\n" + "    if (\"DISABLED\" == name) {\r\n"
					+ "       logInfo(\"Scenario is disabled, skipping timer task ${callerName}\");\r\n" + "       return true;\r\n" + "    }\r\n" + "    return false;\r\n"
					+ "};\r\n" + "\r\n" + "  Integer loadInstrumentAssumptions(Map instrumentAssumptions,String tableName){\r\n"
					+ "    Table instAssumptions = USE DS=\"AMI\" EXECUTE SELECT * FROM ${tableName};\r\n" + "    for (Row row : instAssumptions.getRows()) {\r\n"
					+ "        Map rowMap = new Map();\r\n" + "        rowMap.put(\"thirtyADQNotional\", row.getValue(\"thirtyADQNotional\"));\r\n"
					+ "        rowMap.put(\"lspCapacityNotional\", row.getValue(\"lspCapacityNotional\"));\r\n"
					+ "        instrumentAssumptions.put(row.getValue(\"symbol\"), rowMap);\r\n" + "    }\r\n" + "    return 1;\r\n" + "};\r\n" + "\r\n"
					+ "  Integer syncActiveInstrumentAssumptions(){\r\n"
					+ "    CREATE TABLE risk_ins_assum AS USE ds=\"StarbasePostgresDB\" EXECUTE SELECT * FROM risk_instrument_input_assumptions;\r\n"
					+ "    Table ins = SELECT * FROM risk_ins_assum;\r\n" + "    List rows = ins.getRows();\r\n" + "    if (null == rows || rows.size() == 0) return 0;\r\n"
					+ "    List vals = new List();\r\n" + "    vals.add(\"\");\r\n" + "    vals.add(\"\");\r\n" + "    for (Row row : rows) {\r\n"
					+ "        String scenarioName = row.getValue(\"scenario_name\");\r\n" + "        String symbol = row.getValue(\"symbol\");\r\n"
					+ "        Double thirtyAdq = row.getValue(\"thirty_adq_notional\");\r\n" + "        Double lspCap = row.getValue(\"lsp_capacity_notional\");\r\n"
					+ "        String val = \"(\\\"${symbol}\\\",\\\"${symbol}\\\",${thirtyAdq},${lspCap}),\";\r\n" + "        if (\"ALIQ_ON\" == scenarioName) {\r\n"
					+ "            vals.set(0,vals.get(0) + val);\r\n" + "        } else {\r\n" + "            vals.set(1,vals.get(1) + val);\r\n" + "        }\r\n" + "    }\r\n"
					+ "    String v = vals.get(0);\r\n" + "    if (strIs(v)) {\r\n" + "        v = v.beforeLast(\",\", false) + \";\";\r\n"
					+ "        INSERT INTO riskInstrumentInputAssumptionsAQON VALUES ${v};\r\n" + "    }\r\n" + "    v = vals.get(1);\r\n" + "    if (strIs(v)) {\r\n"
					+ "        v = v.beforeLast(\",\", false) + \";\";\r\n" + "        INSERT INTO riskInstrumentInputAssumptionsAQOFF VALUES ${v};\r\n" + "    }\r\n"
					+ "    return 1;\r\n" + "};\r\n" + "\r\n" + "  Integer syncInitRiskConfigurations(){\r\n"
					+ "    CREATE TABLE risk_configs_ins AS USE ds=\"StarbasePostgresDB\" EXECUTE SELECT * FROM risk_configuration_input_assumptions;\r\n"
					+ "    Table configs = SELECT * FROM risk_configs_ins;\r\n" + "    List configRows = configs.getRows();\r\n"
					+ "    if (null == configRows || configRows.size() == 0) return 0;\r\n" + "    List vals = new List();\r\n" + "    vals.add(\"\");\r\n"
					+ "    vals.add(\"\");\r\n" + "    for (Row r : configRows) {\r\n" + "        String scenarioName = r.getValue(\"scenario_name\");\r\n"
					+ "        String val = \"(\\\"default\\\",${(Double)(r.getValue(\"adq_breach\"))},${(Double)(r.getValue(\"adq_liq_pcnt_of_adq_breach\"))},\r\n"
					+ "            ${(Double)(r.getValue(\"adq_liq_pcnt_of_adq_not_breach\"))},${(Double)(r.getValue(\"lsp_positive_equity\"))},${(Double)(r.getValue(\"lsp_negative_equity\"))},\r\n"
					+ "            ${(Double)(r.getValue(\"lsp_locked_cap_notional\"))},${(Double)(r.getValue(\"insurance_fund_contrib\"))},${(Double)(r.getValue(\"insurance_absorb_pcnt\"))}),\";\r\n"
					+ "        if (\"ALIQ_ON\" == scenarioName) {\r\n" + "            vals.set(0,vals.get(0) + val);\r\n" + "        } else {\r\n"
					+ "            vals.set(1,vals.get(1) + val);\r\n" + "        }\r\n" + "    }\r\n" + "    String v = vals.get(0);\r\n" + "    if (strIs(v)) {\r\n"
					+ "        v = v.beforeLast(\",\", false) + \";\";\r\n"
					+ "        INSERT INTO riskConfigurationInputAssumptionsAQON (I,adqBreach,autoLiqPcntOfADQBreach,autoLiqPcntOfADQNotBreach,lspPositiveEquity,lspNegativeEquity,lspLockedCapNotional,insuranceFundContrib,insuranceAbsorbPcnt)\r\n"
					+ "        VALUES ${v};\r\n" + "    }\r\n" + "        v = vals.get(1);\r\n" + "    if (strIs(v)) {\r\n" + "        v = v.beforeLast(\",\", false) + \";\";\r\n"
					+ "        INSERT INTO riskConfigurationInputAssumptionsAQOFF (I,adqBreach,autoLiqPcntOfADQBreach,autoLiqPcntOfADQNotBreach,lspPositiveEquity,lspNegativeEquity,lspLockedCapNotional,insuranceFundContrib,insuranceAbsorbPcnt)\r\n"
					+ "        VALUES ${v};\r\n" + "    }\r\n" + "    return 1;\r\n" + "};\r\n" + "\r\n" + "  Integer syncScenarios(){\r\n"
					+ "    CREATE TABLE scenario_tmp AS USE ds=\"StarbasePostgresDB\" EXECUTE SELECT * FROM risk_scenarios;\r\n" + "    Table sc = SELECT * FROM scenario_tmp;\r\n"
					+ "    List rows = sc.getRows();\r\n" + "    for (Row r : rows) {\r\n" + "        String name = r.getValue(\"scenario_name\");\r\n"
					+ "        INSERT INTO scenarios (active,scenarioName,activeConfigInputsAssumptionsTable,activeInstrumentAssumptionsTable, activeConfigCMLIQPercentsTable,I)\r\n"
					+ "        VALUES(r.getValue(\"active\"),name,r.getValue(\"config_inputs_assumptions_table\"),r.getValue(\"instrument_inputs_assumptions_table\"),r.getValue(\"config_cm_liq_percents_table\"),name);\r\n"
					+ "    }\r\n" + "    return 1;\r\n" + "};";
			Set<String> constVarsSink = new HashSet<String>();
			StringBuilder errorSink = new StringBuilder();
			DerivedCellCalculator t = parseAmiScript(service.getDebugManager(), AmiDebugMessage.TYPE_CUSTOM_METHODS, null, code, null, errorSink, false, false, constVarsSink);
			if (t instanceof DerivedCellCalculatorBlock) {
				DerivedCellCalculatorBlock block = (DerivedCellCalculatorBlock) t;
				if (block.getMethodFactory() != null) {
					List<MethodFactory> sink = new ArrayList<MethodFactory>();
					block.getMethodFactory().getMethodFactories(sink);
					BasicMethodFactory mf = new BasicMethodFactory();
					for (MethodFactory i : sink)
						mf.addFactory(i);

					this.sink = sink;
				}

			}
			//			SqlProcessor processor = new SqlProcessor();
			//			if (SH.is(data)) {
			//				DerivedCellCalculatorExpression calc = prepareSql(processor, data, EmptyCalcTypes.INSTANCE, true, false,
			//						new AmiWebTopCalcFrameStack(new TablesetImpl(), 64, new DerivedCellTimeoutController(60000), "AMI", service.getBreakpointManager(),
			//								this.scriptManager.getMethodFactory(), EmptyCalcFrame.INSTANCE, EmptyCalcFrame.INSTANCE, service.getDebugManager(), service, "onProcess", (byte) 14,
			//								null, "", EmptyCalcFrame.INSTANCE));
			//				//				executeSql(calc, EmptyCalcFrame.INSTANCE, (byte) 1, new DerivedCellTimeoutController(60000), -1, new AmiWebPlanListener(service.getDebugManager()),
			//				//						new AmiWebTopCalcFrameStack(new TablesetImpl(), 64, new DerivedCellTimeoutController(60000), "AMI", service.getBreakpointManager(),
			//				//								this.scriptManager.getMethodFactory(), EmptyCalcFrame.INSTANCE, EmptyCalcFrame.INSTANCE, service.getDebugManager(), service, "onProcess", (byte) 14,
			//				//								null, "", EmptyCalcFrame.INSTANCE));
			//				DerivedCellCalculator methodCalc = calc.getInnerCalcAt(0).getInnerCalcAt(0);
			//				System.out.println(calc);
			//			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private DerivedCellCalculator parseAmiScript(AmiDebugManager debugManager, byte debugType, String callback, String amiscript, com.f1.base.CalcTypes classTypes,
			StringBuilder errorSink, boolean isTemplate, boolean throwException, Set<String> constVarsSink) {
		if (SH.isnt(amiscript))
			return null;
		Node node;
		node = this.expressionParser.parse(amiscript);
		return toCalc(node, classTypes, constVarsSink);

	}

	public DerivedCellCalculator toCalc(Node formula, com.f1.base.CalcTypes variables, Set<String> constVarsSink) {
		return this.amiScriptParser.toCalcFromNode(formula, newAmiWebDerivedCellParserContextImpl(constVarsSink, variables, this.scriptManager.getMethodFactory()));
	}

	private CalcTypesStack newAmiWebDerivedCellParserContextImpl(Set<String> constVarsSink, CalcTypes varTypes, MethodFactoryManager methodFactory) {
		return new AmiWebCalcTypesStack(constVarsSink, varTypes, EmptyCalcFrame.INSTANCE, methodFactory, EmptyCalcFrame.INSTANCE);
	}

	public static DerivedCellCalculatorExpression prepareSql(SqlProcessor processor, String sql, CalcTypes types, boolean allowImplicitBlock, boolean allowSqlInjection,
			CalcTypesStack stack) {
		try {
			if (!allowImplicitBlock)
				processor.getExpressionParser().setAllowImplicitBlock(false);
			if (!allowSqlInjection)
				processor.getExpressionParser().setAllowSqlInjection(false);
			return processor.toCalc(sql, new ChildCalcTypesStack(stack, false, types));
		} finally {
			if (!allowImplicitBlock)
				processor.getExpressionParser().setAllowImplicitBlock(true);
			if (!allowSqlInjection)
				processor.getExpressionParser().setAllowSqlInjection(true);
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
	public void onSpecialKeyPressed(FormPortlet formPortlet, FormPortletField<?> field, int keycode, int mask, int cursorPosition) {
		if (field instanceof AmiWebFormPortletAmiScriptField)
			((AmiWebFormPortletAmiScriptField) field).onSpecialKeyPressed(formPortlet, field, keycode, mask, cursorPosition);

	}

	@Override
	public String prepareUseClause() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public String preparePreUseClause() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public String exportToText() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void importFromText(String text, StringBuilder sink) {
		// TODO Auto-generated method stub

	}

	@Override
	public void enableEdit(boolean enable) {
		// TODO Auto-generated method stub

	}

	@Override
	public void onUserDblclick(FastWebColumns columns, String action, Map<String, String> properties) {
		// TODO Auto-generated method stub

	}

	@Override
	public void onContextMenu(WebTable table, String action) {
		// TODO Auto-generated method stub

	}

	@Override
	public void onCellClicked(WebTable table, Row row, WebColumn col) {
		// TODO Auto-generated method stub

	}

	@Override
	public void onCellMousedown(WebTable table, Row row, WebColumn col) {
		// TODO Auto-generated method stub

	}

	@Override
	public void onSelectedChanged(FastWebTable fastWebTable) {
		// TODO Auto-generated method stub

	}

	@Override
	public void onNoSelectedChanged(FastWebTable fastWebTable) {
		// TODO Auto-generated method stub

	}

	@Override
	public void onScroll(int viewTop, int viewPortHeight, long contentWidth, long contentHeight) {
		// TODO Auto-generated method stub

	}

	@Override
	public WebMenu createMenu(WebTable table) {
		// TODO Auto-generated method stub
		return null;
	}

	//The abilities to query the backend
	@Override
	public void onBackendResponse(ResultMessage<Action> result) {
		if (result.getError() != null) {
			getManager().showAlert("Internal Error:" + result.getError().getMessage(), result.getError());
			return;
		}
		AmiCenterQueryDsResponse response = (AmiCenterQueryDsResponse) result.getAction();
		Action a = result.getRequestMessage().getAction();
		String query = null;
		if (a instanceof AmiCenterQueryDsRequest) {
			AmiCenterQueryDsRequest request = (AmiCenterQueryDsRequest) a;
			query = request.getQuery();
		}
		if (response.getOk() && response.getTables().size() == 1) {
			Table t = response.getTables().get(0);
			if (query.startsWith("DESCRIBE METHOD")) {
				AdminNode an = AmiCenterManagerUtils.scriptToAdminNode((String) t.getRow(0).get("SQL"));
				MethodDeclarationNode dn = (MethodDeclarationNode) an.getInnerNode(0).getInnerNode(0);
				int paramsCnt = dn.getParamsCount();
				String[] paramNames = new String[paramsCnt];
				Class[] paramClzzs = new Class[paramsCnt];
				String returnType = dn.getReturnType();
				String methodName = dn.getMethodName();

				for (int i = 0; i < paramsCnt; i++) {
					DeclarationNode n = dn.getParamAt(i);
					String name = n.getVarname();
					String type = n.getVartype();
					paramNames[i] = name;
					try {
						paramClzzs[i] = this.bodyField.getCenterMethodFactory().forName(type);
					} catch (ClassNotFoundException e1) {
						LH.warning(log, "Class Not found" + name);
					}
				}
				try {
					Class<?> returnTypeClzz = this.bodyField.getCenterMethodFactory().forName(returnType);
					DeclaredMethodFactory dmf = new DeclaredMethodFactory(returnTypeClzz, methodName, paramNames, paramClzzs, (byte) 0);
					BasicDerivedCellParser cp = new BasicDerivedCellParser(new SqlExpressionParser());
					com.f1.base.CalcTypes types = dmf.getDefinition().getParamTypesMapping();
					CalcTypesTuple2 types2 = new CalcTypesTuple2(types, EmptyCalcTypes.INSTANCE);
					//					ChildCalcFrameStack context = new ChildCalcFrameStack(new BlockNode(0, new ArrayList<Node>(dn)), true, null, EmptyCalcFrame.INSTANCE,
					//							this.scriptManager.getMethodFactory());
					//DerivedCellCalculator calc = cp.toCalc(dn.getBody(), new ChildCalcTypesStack(null, false, types2, this.scriptManager.getMethodFactory()));
					dmf.setInner(dn.geBodytText(), dn.getBodytStart(), dn.getBodytEnd(), null);//calc can be set to null
					this.scriptManager.addManagedMethodFactory(dmf);
					this.bodyField.addMethodFactory(dmf);
				} catch (ClassNotFoundException e1) {
					LH.warning(log, "Class Not found" + returnType);
				}

			} else if (query.startsWith("SHOW FULL METHODS")) {
				for (Row r : t.getRows()) {
					String definition = (String) r.get("Definition");
					sendQueryToBackend("DESCRIBE METHOD " + definition);
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
		return true;
	}

}
