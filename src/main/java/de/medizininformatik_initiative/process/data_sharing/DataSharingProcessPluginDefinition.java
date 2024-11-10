package de.medizininformatik_initiative.process.data_sharing;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import de.medizininformatik_initiative.process.data_sharing.spring.config.DataSharingConfig;
import de.medizininformatik_initiative.process.data_sharing.spring.config.DataSharingVariablesConfig;
import de.medizininformatik_initiative.process.data_sharing.spring.config.DicFhirClientConfig;
import de.medizininformatik_initiative.process.data_sharing.spring.config.DmsFhirClientConfig;
import dev.dsf.bpe.v1.ProcessPluginDefinition;

public class DataSharingProcessPluginDefinition implements ProcessPluginDefinition
{
	public static final String VERSION = "1.0.2.0";
	public static final LocalDate RELEASE_DATE = LocalDate.of(2024, 11, 10);

	@Override
	public String getName()
	{
		return "mii-process-data-sharing";
	}

	@Override
	public String getVersion()
	{
		return VERSION;
	}

	@Override
	public LocalDate getReleaseDate()
	{
		return RELEASE_DATE;
	}

	@Override
	public List<String> getProcessModels()
	{
		return List.of("bpe/coordinate.bpmn", "bpe/execute.bpmn", "bpe/merge.bpmn");
	}

	@Override
	public List<Class<?>> getSpringConfigurations()
	{
		return List.of(DataSharingConfig.class, DataSharingVariablesConfig.class, DicFhirClientConfig.class,
				DmsFhirClientConfig.class);
	}

	@Override
	public Map<String, List<String>> getFhirResourcesByProcessId()
	{
		var aCoo = "fhir/ActivityDefinition/data-sharing-coordinate.xml";
		var aExe = "fhir/ActivityDefinition/data-sharing-execute.xml";
		var aMer = "fhir/ActivityDefinition/data-sharing-merge.xml";

		var cCrypto = "fhir/CodeSystem/mii-cryptography.xml";
		var cDaSeSt = "fhir/CodeSystem/mii-data-set-status.xml";
		var cDaSh = "fhir/CodeSystem/data-sharing.xml";

		var eDaSeStEr = "fhir/StructureDefinition/extension-data-set-status-error.xml";

		var nPrId = "fhir/NamingSystem/mii-project-identifier.xml";

		var qReCon = "fhir/Questionnaire/questionnaire-release-consolidate-data-sets.xml";
		var qReExe = "fhir/Questionnaire/questionnaire-release-data-set.xml";
		var qReMer = "fhir/Questionnaire/questionnaire-release-merged-data-set.xml";

		var sEmedId = "fhir/StructureDefinition/extension-dic-identifier.xml";
		var sTcon = "fhir/StructureDefinition/task-consolidate-data-sets.xml";
		var sTcoo = "fhir/StructureDefinition/task-coordinate-data-sharing.xml";
		var sTexe = "fhir/StructureDefinition/task-execute-data-sharing.xml";
		var sTmer = "fhir/StructureDefinition/task-merge-data-sharing.xml";
		var sTsen = "fhir/StructureDefinition/task-send-data-set.xml";
		var sTsenMer = "fhir/StructureDefinition/task-merged-data-set.xml";
		var sTsenRecHrp = "fhir/StructureDefinition/task-received-data-set.xml";
		var sTsenRecDic = "fhir/StructureDefinition/task-status-data-set.xml";
		var sTstExe = "fhir/StructureDefinition/task-stop-execute-data-sharing.xml";

		var tCoo = "fhir/Task/task-coordinate-data-sharing.xml";

		var vCrypto = "fhir/ValueSet/mii-cryptography.xml";
		var vDaSeStRe = "fhir/ValueSet/mii-data-set-status-receive.xml";
		var vDaSeStSe = "fhir/ValueSet/mii-data-set-status-send.xml";
		var vDaSh = "fhir/ValueSet/data-sharing.xml";

		return Map.of( //
				ConstantsDataSharing.PROCESS_NAME_FULL_COORDINATE_DATA_SHARING, //
				List.of(aCoo, cDaSh, nPrId, qReCon, sTcoo, sTsenMer, sTsenRecHrp, tCoo, vDaSh), //
				ConstantsDataSharing.PROCESS_NAME_FULL_EXECUTE_DATA_SHARING, //
				List.of(aExe, cDaSeSt, cDaSh, eDaSeStEr, nPrId, qReExe, sTexe, sTsenRecDic, sTstExe, vDaSeStSe, //
						vDaSh), //
				ConstantsDataSharing.PROCESS_NAME_FULL_MERGE_DATA_SHARING, //
				List.of(aMer, cDaSeSt, cCrypto, cDaSh, eDaSeStEr, nPrId, qReMer, sEmedId, sTcon, sTmer, sTsen, //
						vCrypto, vDaSeStRe, vDaSh));
	}
}
