package de.medizininformatik_initiative.process.data_sharing.spring.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;

import de.medizininformatik_initiative.process.data_sharing.DataSharingProcessPluginDeploymentStateListener;
import de.medizininformatik_initiative.process.data_sharing.message.SendDataSet;
import de.medizininformatik_initiative.process.data_sharing.message.SendExecuteDataSharing;
import de.medizininformatik_initiative.process.data_sharing.message.SendInitializeNewProjectDataSharing;
import de.medizininformatik_initiative.process.data_sharing.message.SendMergeDataSharing;
import de.medizininformatik_initiative.process.data_sharing.message.SendMergedDataSet;
import de.medizininformatik_initiative.process.data_sharing.message.SendReceipt;
import de.medizininformatik_initiative.process.data_sharing.message.SendReceivedDataSet;
import de.medizininformatik_initiative.process.data_sharing.questionnaire.ReleaseDataSetListener;
import de.medizininformatik_initiative.process.data_sharing.questionnaire.ReleaseMergedDataSetListener;
import de.medizininformatik_initiative.process.data_sharing.service.coordinate.CommunicateMissingDataSetsCoordinate;
import de.medizininformatik_initiative.process.data_sharing.service.coordinate.CommunicateReceivedDataSet;
import de.medizininformatik_initiative.process.data_sharing.service.coordinate.ExtractMergedDataSetUrl;
import de.medizininformatik_initiative.process.data_sharing.service.coordinate.PrepareCoordination;
import de.medizininformatik_initiative.process.data_sharing.service.coordinate.SelectDicTargets;
import de.medizininformatik_initiative.process.data_sharing.service.coordinate.SelectDmsTarget;
import de.medizininformatik_initiative.process.data_sharing.service.execute.CheckQuestionnaireDataSetReleaseInput;
import de.medizininformatik_initiative.process.data_sharing.service.execute.CreateDataSetBundle;
import de.medizininformatik_initiative.process.data_sharing.service.execute.DeleteDataSet;
import de.medizininformatik_initiative.process.data_sharing.service.execute.EncryptDataSet;
import de.medizininformatik_initiative.process.data_sharing.service.execute.HandleErrorExecute;
import de.medizininformatik_initiative.process.data_sharing.service.execute.HandleReceipt;
import de.medizininformatik_initiative.process.data_sharing.service.execute.PrepareExecution;
import de.medizininformatik_initiative.process.data_sharing.service.execute.ReadDataSet;
import de.medizininformatik_initiative.process.data_sharing.service.execute.SelectDataSetTarget;
import de.medizininformatik_initiative.process.data_sharing.service.execute.StoreDataSet;
import de.medizininformatik_initiative.process.data_sharing.service.execute.ValidateDataSetExecute;
import de.medizininformatik_initiative.process.data_sharing.service.merge.CheckQuestionnaireMergedDataSetReleaseInput;
import de.medizininformatik_initiative.process.data_sharing.service.merge.CommunicateMissingDataSetsMerge;
import de.medizininformatik_initiative.process.data_sharing.service.merge.DecryptDataSet;
import de.medizininformatik_initiative.process.data_sharing.service.merge.DownloadDataSet;
import de.medizininformatik_initiative.process.data_sharing.service.merge.HandleErrorMergeReceiveDownloadInsert;
import de.medizininformatik_initiative.process.data_sharing.service.merge.HandleErrorMergeReceiveSendReceipt;
import de.medizininformatik_initiative.process.data_sharing.service.merge.HandleErrorMergeRelease;
import de.medizininformatik_initiative.process.data_sharing.service.merge.InsertDataSet;
import de.medizininformatik_initiative.process.data_sharing.service.merge.PrepareMerging;
import de.medizininformatik_initiative.process.data_sharing.service.merge.ReinsertTarget;
import de.medizininformatik_initiative.process.data_sharing.service.merge.SelectDicTarget;
import de.medizininformatik_initiative.process.data_sharing.service.merge.SelectHrpTarget;
import de.medizininformatik_initiative.process.data_sharing.service.merge.ValidateDataSetMerge;
import de.medizininformatik_initiative.processes.common.crypto.KeyProvider;
import de.medizininformatik_initiative.processes.common.crypto.KeyProviderImpl;
import de.medizininformatik_initiative.processes.common.util.DataSetStatusGenerator;
import de.medizininformatik_initiative.processes.common.util.MimeTypeHelper;
import dev.dsf.bpe.v1.ProcessPluginApi;
import dev.dsf.bpe.v1.ProcessPluginDeploymentStateListener;
import dev.dsf.bpe.v1.documentation.ProcessDocumentation;

@Configuration
@ComponentScan(basePackages = "de.medizininformatik_initiative")
public class DataSharingConfig
{
	@Autowired
	private ProcessPluginApi api;

	@Autowired
	private DicFhirClientConfig dicFhirClientConfig;

	@Autowired
	private DmsFhirClientConfig dmsFhirClientConfig;

	@ProcessDocumentation(required = true, processNames = {
			"medizininformatik-initiativede_dataReceive" }, description = "Location of the DMS private-key as 4096 Bit RSA PEM encoded, not encrypted file", recommendation = "Use docker secret file to configure", example = "/run/secrets/dms_private_key.pem")
	@Value("${de.medizininformatik.initiative.dms.private.key:#{null}}")
	private String dmsPrivateKeyFile;

	@ProcessDocumentation(required = true, processNames = {
			"medizininformatik-initiativede_dataReceive" }, description = "Location of the DMS public-key as 4096 Bit RSA PEM encoded file", recommendation = "Use docker secret file to configure", example = "/run/secrets/dms_public_key.pem")
	@Value("${de.medizininformatik.initiative.dms.public.key:#{null}}")
	private String dmsPublicKeyFile;

	// all Processes

	@Bean
	@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
	public MimeTypeHelper mimeTypeHelper()
	{
		return new MimeTypeHelper(api.getFhirContext());
	}

	@Bean
	@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
	public KeyProvider keyProviderDic()
	{
		return KeyProviderImpl.fromFiles(api, null, null, dicFhirClientConfig.dataLogger());
	}

	@Bean
	@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
	public KeyProvider keyProviderDms()
	{
		return KeyProviderImpl.fromFiles(api, dmsPrivateKeyFile, dmsPublicKeyFile, dmsFhirClientConfig.dataLogger());
	}

	@Bean
	@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
	public DataSetStatusGenerator dataSetStatusGenerator()
	{
		return new DataSetStatusGenerator();
	}

	@Bean
	@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
	public ProcessPluginDeploymentStateListener dataSharingProcessPluginDeploymentStateListener()
	{
		return new DataSharingProcessPluginDeploymentStateListener(dicFhirClientConfig.fhirClientFactory(),
				dmsFhirClientConfig.fhirClientFactory(), keyProviderDms());
	}


	// coordinateDataSharing

	@Bean
	@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
	public PrepareCoordination prepareCoordination()
	{
		return new PrepareCoordination(api);
	}

	@Bean
	@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
	public SelectDicTargets selectDicTargets()
	{
		return new SelectDicTargets(api);
	}

	@Bean
	@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
	public SelectDmsTarget selectDmsTarget()
	{
		return new SelectDmsTarget(api);
	}

	@Bean
	@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
	public SendMergeDataSharing sendMergeDataSharing()
	{
		return new SendMergeDataSharing(api);
	}

	@Bean
	@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
	public SendExecuteDataSharing sendExecuteDataSharing()
	{
		return new SendExecuteDataSharing(api);
	}

	@Bean
	@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
	public CommunicateReceivedDataSet communicateReceivedDataSet()
	{
		return new CommunicateReceivedDataSet(api);
	}

	@Bean
	@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
	public CommunicateMissingDataSetsCoordinate communicateMissingDataSetsCoordinate()
	{
		return new CommunicateMissingDataSetsCoordinate(api);
	}

	@Bean
	@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
	public ExtractMergedDataSetUrl extractMergedDataSetUrl()
	{
		return new ExtractMergedDataSetUrl(api);
	}

	// executeDataSharing

	@Bean
	@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
	public PrepareExecution prepareExecution()
	{
		return new PrepareExecution(api);
	}

	@Bean
	@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
	public ReleaseDataSetListener releaseDataSetListener()
	{
		return new ReleaseDataSetListener(api, dicFhirClientConfig.fhirClientFactory());
	}

	@Bean
	@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
	public HandleErrorExecute handleErrorExecute()
	{
		return new HandleErrorExecute(api);
	}

	@Bean
	@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
	public CheckQuestionnaireDataSetReleaseInput checkQuestionnaireDataSetReleaseInput()
	{
		return new CheckQuestionnaireDataSetReleaseInput(api);
	}

	@Bean
	@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
	public SelectDataSetTarget selectDataSetTarget()
	{
		return new SelectDataSetTarget(api);
	}

	@Bean
	@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
	public ReadDataSet readDataSet()
	{
		return new ReadDataSet(api, dicFhirClientConfig.fhirClientFactory());
	}

	@Bean
	@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
	public ValidateDataSetExecute validateDataSetExecute()
	{
		return new ValidateDataSetExecute(api, mimeTypeHelper());
	}

	@Bean
	@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
	public CreateDataSetBundle createDataSetBundle()
	{
		return new CreateDataSetBundle(api, dicFhirClientConfig.dataLogger());
	}

	@Bean
	@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
	public EncryptDataSet encryptDataSet()
	{
		return new EncryptDataSet(api, keyProviderDic());
	}

	@Bean
	@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
	public StoreDataSet storeDataSet()
	{
		return new StoreDataSet(api, dicFhirClientConfig.dataLogger());
	}

	@Bean
	@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
	public SendDataSet sendDataSet()
	{
		return new SendDataSet(api, dataSetStatusGenerator());
	}

	@Bean
	@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
	public DeleteDataSet deleteDataSet()
	{
		return new DeleteDataSet(api);
	}

	@Bean
	@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
	public HandleReceipt handleReceipt()
	{
		return new HandleReceipt(api, dataSetStatusGenerator());
	}

	// mergeDataSharing

	@Bean
	@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
	public PrepareMerging prepareMerging()
	{
		return new PrepareMerging(api);
	}

	@Bean
	@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
	public SendInitializeNewProjectDataSharing sendInitializeNewProjectDataSharing()
	{
		return new SendInitializeNewProjectDataSharing(api, dmsFhirClientConfig.fhirClientFactory());
	}

	@Bean
	@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
	public DownloadDataSet downloadDataSet()
	{
		return new DownloadDataSet(api, dataSetStatusGenerator());
	}

	@Bean
	@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
	public DecryptDataSet decryptDataSet()
	{
		return new DecryptDataSet(api, keyProviderDms(), dmsFhirClientConfig.dataLogger(), dataSetStatusGenerator());
	}

	@Bean
	@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
	public ValidateDataSetMerge validateDataSetMerge()
	{
		return new ValidateDataSetMerge(api, mimeTypeHelper(), dataSetStatusGenerator());
	}

	@Bean
	@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
	public InsertDataSet insertDataSet()
	{
		return new InsertDataSet(api, dmsFhirClientConfig.fhirClientFactory(), dataSetStatusGenerator());
	}

	@Bean
	@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
	public HandleErrorMergeReceiveDownloadInsert handleErrorMergeReceiveDownloadInsert()
	{
		return new HandleErrorMergeReceiveDownloadInsert(api);
	}

	@Bean
	@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
	public HandleErrorMergeReceiveSendReceipt handleErrorMergeReceiveSendReceipt()
	{
		return new HandleErrorMergeReceiveSendReceipt(api);
	}

	@Bean
	@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
	public SelectDicTarget selectDicTarget()
	{
		return new SelectDicTarget(api);
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
		return new ReinsertTarget(api);
	}

	@Bean
	@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
	public SendReceivedDataSet sendReceivedDataSet()
	{
		return new SendReceivedDataSet(api);
	}

	@Bean
	@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
	public HandleErrorMergeRelease handleErrorMergeRelease()
	{
		return new HandleErrorMergeRelease(api);
	}

	@Bean
	@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
	public CommunicateMissingDataSetsMerge communicateMissingDataSetsMerge()
	{
		return new CommunicateMissingDataSetsMerge(api);
	}

	@Bean
	@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
	public ReleaseMergedDataSetListener releaseMergedDataSetListener()
	{
		return new ReleaseMergedDataSetListener(api);
	}

	@Bean
	@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
	public CheckQuestionnaireMergedDataSetReleaseInput checkQuestionnaireMergedDataSetReleaseInput()
	{
		return new CheckQuestionnaireMergedDataSetReleaseInput(api);
	}

	@Bean
	@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
	public SelectHrpTarget selectHrpTarget()
	{
		return new SelectHrpTarget(api);
	}

	@Bean
	@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
	public SendMergedDataSet sendMergedDataSet()
	{
		return new SendMergedDataSet(api);
	}
}
