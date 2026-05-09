package de.medizininformatik_initiative.process.data_sharing.spring.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;

import de.medizininformatik_initiative.process.data_sharing.DataSharingProcessPluginDeploymentListener;
import de.medizininformatik_initiative.process.data_sharing.message.SendConsolidateDataSets;
import de.medizininformatik_initiative.process.data_sharing.message.SendDataSet;
import de.medizininformatik_initiative.process.data_sharing.message.SendExecuteDataSharing;
import de.medizininformatik_initiative.process.data_sharing.message.SendInitializeNewProjectDataSharing;
import de.medizininformatik_initiative.process.data_sharing.message.SendMergeDataSharing;
import de.medizininformatik_initiative.process.data_sharing.message.SendMergedDataSet;
import de.medizininformatik_initiative.process.data_sharing.message.SendReceipt;
import de.medizininformatik_initiative.process.data_sharing.message.SendReceivedDataSet;
import de.medizininformatik_initiative.process.data_sharing.message.SendStopExecuteDataSharing;
import de.medizininformatik_initiative.process.data_sharing.questionnaire.ReleaseConsolidateDataSetsListener;
import de.medizininformatik_initiative.process.data_sharing.questionnaire.ReleaseDataSetListener;
import de.medizininformatik_initiative.process.data_sharing.questionnaire.ReleaseMergedDataSetListener;
import de.medizininformatik_initiative.process.data_sharing.service.coordinate.CheckQuestionnaireConsolidateDataSetsReleaseInput;
import de.medizininformatik_initiative.process.data_sharing.service.coordinate.CheckReceivedDataSets;
import de.medizininformatik_initiative.process.data_sharing.service.coordinate.CommunicateMissingDataSetsCoordinate;
import de.medizininformatik_initiative.process.data_sharing.service.coordinate.CommunicateReceivedDataSet;
import de.medizininformatik_initiative.process.data_sharing.service.coordinate.ExtractMergedDataSetUrl;
import de.medizininformatik_initiative.process.data_sharing.service.coordinate.HandleErrorCoordinate;
import de.medizininformatik_initiative.process.data_sharing.service.coordinate.PrepareCoordination;
import de.medizininformatik_initiative.process.data_sharing.service.coordinate.SelectDicTargets;
import de.medizininformatik_initiative.process.data_sharing.service.coordinate.SelectDmsTarget;
import de.medizininformatik_initiative.process.data_sharing.service.execute.CheckQuestionnaireDataSetReleaseInput;
import de.medizininformatik_initiative.process.data_sharing.service.execute.DeleteDataSet;
import de.medizininformatik_initiative.process.data_sharing.service.execute.EncryptAndStoreDataSet;
import de.medizininformatik_initiative.process.data_sharing.service.execute.HandleErrorExecute;
import de.medizininformatik_initiative.process.data_sharing.service.execute.HandleReceipt;
import de.medizininformatik_initiative.process.data_sharing.service.execute.PrepareExecution;
import de.medizininformatik_initiative.process.data_sharing.service.execute.ReadDataSet;
import de.medizininformatik_initiative.process.data_sharing.service.execute.SelectDataSetTarget;
import de.medizininformatik_initiative.process.data_sharing.service.execute.StopReleaseDataSet;
import de.medizininformatik_initiative.process.data_sharing.service.execute.ValidateDataSetExecute;
import de.medizininformatik_initiative.process.data_sharing.service.merge.CheckQuestionnaireMergedDataSetReleaseInput;
import de.medizininformatik_initiative.process.data_sharing.service.merge.CommunicateMissingDataSetsMerge;
import de.medizininformatik_initiative.process.data_sharing.service.merge.DecryptValidateAndInsertDataSet;
import de.medizininformatik_initiative.process.data_sharing.service.merge.DownloadDataSet;
import de.medizininformatik_initiative.process.data_sharing.service.merge.HandleErrorMergeReceiveDownloadInsert;
import de.medizininformatik_initiative.process.data_sharing.service.merge.HandleErrorMergeReceiveSendReceipt;
import de.medizininformatik_initiative.process.data_sharing.service.merge.HandleErrorMergeRelease;
import de.medizininformatik_initiative.process.data_sharing.service.merge.PrepareMerging;
import de.medizininformatik_initiative.process.data_sharing.service.merge.ReinsertTarget;
import de.medizininformatik_initiative.process.data_sharing.service.merge.SelectDicTarget;
import de.medizininformatik_initiative.process.data_sharing.service.merge.SelectHrpTarget;
import de.medizininformatik_initiative.processes.common.crypto.KeyProvider;
import de.medizininformatik_initiative.processes.common.util.DataSetStatusGenerator;
import dev.dsf.bpe.v2.ProcessPluginApi;
import dev.dsf.bpe.v2.ProcessPluginDeploymentListener;
import dev.dsf.bpe.v2.documentation.ProcessDocumentation;

@Configuration
@ComponentScan(basePackages = "de.medizininformatik_initiative")
public class DataSharingConfig
{
	@Autowired
	private ProcessPluginApi api;

	@ProcessDocumentation(processNames = {
			"medizininformatik-initiativede_coordinateDataSharing" }, description = "To receive e-mails as HRP, set to `true`")
	@Value("${de.medizininformatik.initiative.data.sharing.hrp.email.enabled:false}")
	private boolean hrpEmailEnabled;

	@ProcessDocumentation(required = true, processNames = {
			"medizininformatik-initiativede_dataSend" }, description = "The ID of a DIC FHIR server from the main DSF configuration as 'DSF FHIR Client'", example = "dic-fhir-store")
	@Value("${de.medizininformatik.initiative.data.sharing.dic.fhir.server.id:#{null}}")
	private String fhirStoreIdDic;

	@ProcessDocumentation(processNames = {
			"medizininformatik-initiativede_executeDataSharing" }, description = "To enable stream processing when reading Binary resources set to `true`")
	@Value("${de.medizininformatik.initiative.data.sharing.dic.fhir.server.binary.stream.read.enabled:false}")
	private boolean fhirBinaryStreamReadEnabled;

	@ProcessDocumentation(processNames = {
			"medizininformatik-initiativede_executeDataSharing" }, description = "If the DIC FHIR server is a HAPI FHIR server and uses external storage for Binary resources via the ENV variable `HAPI_FHIR_BINARY_STORAGE_ENABLED`, set this ENV variable as well to `true`")
	@Value("${de.medizininformatik.initiative.data.sharing.dic.fhir.server.binary.stream.read.use.hapi.blob.storage.operation:false}")
	private boolean fhirBinaryStreamReadUseHapiBlobStorageOperation;

	@ProcessDocumentation(processNames = {
			"medizininformatik-initiativede_executeDataSharing" }, description = "To receive e-mails as DIC, set to `true`")
	@Value("${de.medizininformatik.initiative.data.sharing.dic.email.enabled:false}")
	private boolean dicEmailEnabled;

	@ProcessDocumentation(processNames = {
			"medizininformatik-initiativede_executeDataSharing" }, description = "The period the process waits to receive the status from the DMS, must be an ISO 8601 time duration pattern")
	@Value("${de.medizininformatik.initiative.data.sharing.dic.status.timer.interval:PT45M}")
	private String statusTimerInterval;

	@ProcessDocumentation(required = true, processNames = {
			"medizininformatik-initiativede_mergeDataSharing" }, description = "The ID of a DIC FHIR server from the main DSF configuration as 'DSF FHIR Client'", example = "dic-fhir-store")
	@Value("${de.medizininformatik.initiative.data.sharing.dms.fhir.server.id:#{null}}")
	private String fhirStoreIdDms;

	@ProcessDocumentation(processNames = {
			"medizininformatik-initiativede_mergeDataSharing" }, description = "To enable stream processing when writing Binary resources set to `true`")
	@Value("${de.medizininformatik.initiative.data.sharing.dms.fhir.server.binary.stream.write.enabled:false}")
	private boolean fhirBinaryStreamWriteEnabled;

	@ProcessDocumentation(processNames = {
			"medizininformatik-initiativede_mergeDataSharing" }, description = "To receive e-mails as DMS, set to `true`")
	@Value("${de.medizininformatik.initiative.data.sharing.dms.email.enabled:false}")
	private boolean dmsEmailEnabled;

	@ProcessDocumentation(required = true, processNames = {
			"medizininformatik-initiativede_mergeDataSharing" }, description = "Location of the DMS private-key as 4096 Bit RSA PEM encoded, not encrypted file", recommendation = "Use docker secret file to configure", example = "/run/secrets/dms_private_key.pem")
	@Value("${de.medizininformatik.initiative.dms.private.key:#{null}}")
	private String dmsPrivateKeyFile;

	@ProcessDocumentation(required = true, processNames = {
			"medizininformatik-initiativede_mergeDataSharing" }, description = "Location of the DMS public-key as 4096 Bit RSA PEM encoded file", recommendation = "Use docker secret file to configure", example = "/run/secrets/dms_public_key.pem")
	@Value("${de.medizininformatik.initiative.dms.public.key:#{null}}")
	private String dmsPublicKeyFile;

	// all Processes

	@Bean
	@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
	public KeyProvider keyProviderDic()
	{
		return KeyProvider.from(api);
	}

	@Bean
	@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
	public KeyProvider keyProviderDms()
	{
		return KeyProvider.from(api, dmsPrivateKeyFile, dmsPublicKeyFile);
	}

	@Bean
	@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
	public DataSetStatusGenerator dataSetStatusGenerator()
	{
		return new DataSetStatusGenerator();
	}

	@Bean
	@Scope(ConfigurableBeanFactory.SCOPE_SINGLETON)
	public ProcessPluginDeploymentListener dataSharingProcessPluginDeploymentListener()
	{
		return new DataSharingProcessPluginDeploymentListener(api, fhirStoreIdDic, fhirStoreIdDms, keyProviderDms());
	}

	// coordinateDataSharing

	@Bean
	@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
	public PrepareCoordination prepareCoordination()
	{
		return new PrepareCoordination();
	}

	@Bean
	@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
	public SelectDicTargets selectDicTargets()
	{
		return new SelectDicTargets();
	}

	@Bean
	@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
	public SelectDmsTarget selectDmsTarget()
	{
		return new SelectDmsTarget();
	}

	@Bean
	@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
	public SendMergeDataSharing sendMergeDataSharing()
	{
		return new SendMergeDataSharing();
	}

	@Bean
	@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
	public SendExecuteDataSharing sendExecuteDataSharing()
	{
		return new SendExecuteDataSharing();
	}

	@Bean
	@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
	public CommunicateReceivedDataSet communicateReceivedDataSet()
	{
		return new CommunicateReceivedDataSet(hrpEmailEnabled);
	}

	@Bean
	@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
	public CheckReceivedDataSets checkReceivedDataSets()
	{
		return new CheckReceivedDataSets();
	}

	@Bean
	@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
	public ReleaseConsolidateDataSetsListener releaseConsolidateDataSetsListener()
	{
		return new ReleaseConsolidateDataSetsListener(hrpEmailEnabled);
	}

	@Bean
	@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
	public CheckQuestionnaireConsolidateDataSetsReleaseInput checkQuestionnaireConsolidateDataSetsReleaseInput()
	{
		return new CheckQuestionnaireConsolidateDataSetsReleaseInput();
	}

	@Bean
	@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
	public SendConsolidateDataSets sendConsolidateDataSets()
	{
		return new SendConsolidateDataSets();
	}

	@Bean
	@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
	public HandleErrorCoordinate handleErrorCoordinate()
	{
		return new HandleErrorCoordinate(hrpEmailEnabled);
	}

	@Bean
	@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
	public CommunicateMissingDataSetsCoordinate communicateMissingDataSetsCoordinate()
	{
		return new CommunicateMissingDataSetsCoordinate(hrpEmailEnabled);
	}

	@Bean
	@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
	public SendStopExecuteDataSharing sendStopExecuteDataSharing()
	{
		return new SendStopExecuteDataSharing();
	}

	@Bean
	@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
	public ExtractMergedDataSetUrl extractMergedDataSetUrl()
	{
		return new ExtractMergedDataSetUrl();
	}

	// executeDataSharing

	@Bean
	@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
	public PrepareExecution prepareExecution()
	{
		return new PrepareExecution(statusTimerInterval);
	}

	@Bean
	@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
	public ReleaseDataSetListener releaseDataSetListener()
	{
		return new ReleaseDataSetListener(fhirStoreIdDic, dicEmailEnabled);
	}

	@Bean
	@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
	public StopReleaseDataSet stopReleaseDataSet()
	{
		return new StopReleaseDataSet();
	}

	@Bean
	@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
	public HandleErrorExecute handleErrorExecute()
	{
		return new HandleErrorExecute(dataSetStatusGenerator(), dicEmailEnabled);
	}

	@Bean
	@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
	public CheckQuestionnaireDataSetReleaseInput checkQuestionnaireDataSetReleaseInput()
	{
		return new CheckQuestionnaireDataSetReleaseInput();
	}

	@Bean
	@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
	public SelectDataSetTarget selectDataSetTarget()
	{
		return new SelectDataSetTarget();
	}

	@Bean
	@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
	public ReadDataSet readDataSet()
	{
		return new ReadDataSet(fhirStoreIdDic, fhirBinaryStreamReadEnabled);
	}

	@Bean
	@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
	public ValidateDataSetExecute validateDataSetExecute()
	{
		return new ValidateDataSetExecute(fhirStoreIdDic, fhirBinaryStreamReadUseHapiBlobStorageOperation);
	}

	@Bean
	@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
	public EncryptAndStoreDataSet encryptAndStoreDataSet()
	{
		return new EncryptAndStoreDataSet(fhirStoreIdDic, fhirBinaryStreamReadUseHapiBlobStorageOperation,
				dataSetStatusGenerator(), keyProviderDic(), dicEmailEnabled);
	}

	@Bean
	@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
	public SendDataSet sendDataSet()
	{
		return new SendDataSet();
	}

	@Bean
	@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
	public DeleteDataSet deleteDataSet()
	{
		return new DeleteDataSet();
	}

	@Bean
	@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
	public HandleReceipt handleReceipt()
	{
		return new HandleReceipt(dataSetStatusGenerator(), dicEmailEnabled);
	}

	// mergeDataSharing

	@Bean
	@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
	public PrepareMerging prepareMerging()
	{
		return new PrepareMerging();
	}

	@Bean
	@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
	public SendInitializeNewProjectDataSharing sendInitializeNewProjectDataSharing()
	{
		return new SendInitializeNewProjectDataSharing(fhirStoreIdDms);
	}

	@Bean
	@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
	public DownloadDataSet downloadDataSet()
	{
		return new DownloadDataSet(dataSetStatusGenerator(), fhirBinaryStreamWriteEnabled);
	}

	@Bean
	@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
	public DecryptValidateAndInsertDataSet decryptValidateAndInsertDataSet()
	{
		return new DecryptValidateAndInsertDataSet(fhirStoreIdDms, keyProviderDms(), dataSetStatusGenerator());
	}

	@Bean
	@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
	public HandleErrorMergeReceiveDownloadInsert handleErrorMergeReceiveDownloadInsert()
	{
		return new HandleErrorMergeReceiveDownloadInsert(dmsEmailEnabled);
	}

	@Bean
	@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
	public HandleErrorMergeReceiveSendReceipt handleErrorMergeReceiveSendReceipt()
	{
		return new HandleErrorMergeReceiveSendReceipt(dmsEmailEnabled);
	}

	@Bean
	@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
	public SelectDicTarget selectDicTarget()
	{
		return new SelectDicTarget();
	}

	@Bean
	@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
	public SendReceipt sendReceipt()
	{
		return new SendReceipt(api, dataSetStatusGenerator());
	}

	@Bean
	@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
	public ReinsertTarget reinsertTarget()
	{
		return new ReinsertTarget();
	}

	@Bean
	@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
	public SendReceivedDataSet sendReceivedDataSet()
	{
		return new SendReceivedDataSet(api, dataSetStatusGenerator());
	}

	@Bean
	@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
	public HandleErrorMergeRelease handleErrorMergeRelease()
	{
		return new HandleErrorMergeRelease();
	}

	@Bean
	@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
	public CommunicateMissingDataSetsMerge communicateMissingDataSetsMerge()
	{
		return new CommunicateMissingDataSetsMerge();
	}

	@Bean
	@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
	public ReleaseMergedDataSetListener releaseMergedDataSetListener()
	{
		return new ReleaseMergedDataSetListener(dmsEmailEnabled);
	}

	@Bean
	@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
	public CheckQuestionnaireMergedDataSetReleaseInput checkQuestionnaireMergedDataSetReleaseInput()
	{
		return new CheckQuestionnaireMergedDataSetReleaseInput();
	}

	@Bean
	@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
	public SelectHrpTarget selectHrpTarget()
	{
		return new SelectHrpTarget();
	}

	@Bean
	@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
	public SendMergedDataSet sendMergedDataSet()
	{
		return new SendMergedDataSet();
	}
}
