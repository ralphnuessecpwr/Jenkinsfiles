import static com.bmc.db2.bmcclient.BMCClientCN.bmcCN;
import static com.bmc.db2.bmcclient.BMCClientSM.bmcSM;
import hudson.model.Result;
boolean debug = true
node {
    bmcCN.reset("BMC_SCHEMA_IDENTICAL")
    bmcCN.reset("BMC_GENERATE_JCL_ONLY")
    bmcCN.reset("BMC_SKIP_CDL_GENERATION")
    bmcCN.reset("BMC_RESET_RC")
	
	bmcSM.reset("BMC_SCHEMA_IDENTICAL")
    bmcSM.reset("BMC_GENERATE_JCL_ONLY")
    bmcSM.reset("BMC_SKIP_CDL_GENERATION")
    bmcSM.reset("BMC_RESET_RC")

    // Flag if Compare Step show no changes
    boolean Compare_Equal
    // Flag if Compare Plugin set to Generate JCL Only
    boolean Gen_Only
    // Flag if Job should start with CDL Import
    boolean Skip_Compare
    // Flag to skip Stages if JCL was not submitted or Compare was equal
    boolean Skip_Stage = false

    // Get Build Number as integer so I can build the JOBID dynamically
    if (debug) {
        echo " ${params.Job_ID}"
        echo "${BUILD_NUMBER}"
    }

    stage('Check in code to Source Code Manager') {
        echo "Source Code Checked in"
        sleep time: 589, unit: 'MILLISECONDS'
    }

    stage('Build Application Project') {
        echo "The application was built"
        sleep time: 2138, unit: 'MILLISECONDS'
    }

    stage('Compare Developer Schema definition against Production') {
        build job: 'Compare DDL',
            parameters: [string(name: 'Job_ID', value: "${Job_ID}"),
                string(name: 'From_SSID', value: "${params.From_SSID}"),
                string(name: 'To_SSID', value: "${params.To_SSID}"),
                string(name: 'DB_Name', value: "${params.DB_Name}"),
                string(name: 'TSO_ID', value: "${params.TSO_ID}"),
                password(name: 'TSO_PSWD', value: "${params.TSO_PSWD}")
            ]

        Compare_Equal = bmcSM.get("BMC_SCHEMA_IDENTICAL")
        Gen_Only = bmcSM.get("BMC_GENERATE_JCL_ONLY")
        Skip_Compare = bmcSM.get("BMC_SKIP_CDL_GENERATION")

        if (debug) {
            println "Return from Compare " + bmcSM.get("BMC_SCHEMA_IDENTICAL")
            println "  Gen JCL Only " + bmcSM.get("BMC_GENERATE_JCL_ONLY")
            println " Skip Compare CDL Gen " + bmcSM.get("BMC_SKIP_CDL_GENERATION")
        }
        if (Compare_Equal) {
            Skip_Stage = true
        } else if (Gen_Only) {
            Skip_Stage = true
        }

        if (!Skip_Stage) {
            build job: 'Download Output',
                parameters: [string(name: 'Job_ID', value: "${Job_ID}"),
                    string(name: 'Output_DIR', value: "${params.Output_DIR}"),
                    string(name: 'TSO_ID', value: "${params.TSO_ID}"),
                    password(name: 'TSO_PSWD', value: "${params.TSO_PSWD}"),
                    string(name: 'TSOACCT', value: "${params.TSOACCT}")
                ]

        } else {
            if (Compare_Equal) {
                println "Compare showed no differences"
            } else if (Gen_Only) {
                println "Compare JCL Generated Only, not submitted"
            }
        }
    }
    
    bmcCN.reset("BMC_RESET_RC")
    stage('Evaluate Change Impact on System') {
        if (!Skip_Stage) {
                build job: 'Evaluate Impact Report',
                    parameters: [string(name: 'Job_ID', value: "${Job_ID}"),
                        string(name: 'TSO_ID', value: "${params.TSO_ID}"),
                        password(name: 'TSO_PSWD', value: "${params.TSO_PSWD}")
                    ]
                    script {
                    JobRC = bmcCN.getRC("BMC_EXEC_RC")
                    echo "JobRC=====start"
                    echo "RC= : " + JobRC
                    echo "JobRC=====end"
                }
                    
                if (JobRC == '0000') {
                    echo "Non destruptive changes received."
                    echo "Automatic deployment in process..."
					stage('Automatic Deployment due to Non Distruptive Changes')
                    build job: 'Execute Changes',
                        parameters: [string(name: 'Job_ID', value: "${Job_ID}"),
                            string(name: 'TSO_ID', value: "${params.TSO_ID}"),
                            password(name: 'TSO_PSWD', value: "${params.TSO_PSWD}")
                        ]
                } else {
                    emailext attachmentsPattern: '${Job_ID}_*.txt',
                body: '''Please review the changed requested by the developer related to the schema definition of the application.
See attached Impact Report and CDL files.  

You can generate a Compare report using the CDL generated.
MVSNXO.DEVOPS.CDL(${Job_ID})
https://bmca.bmc.com:3683/commandcenter/index.html?perspective=SchemaManager&cdl=MVSNXO.DEVOPS.CDL(${Job_Id})

You can approve or reject implementation of changes on Subsystem ${To_SSID} by using this link:
${BUILD_URL}/console''',
                subject: '$PROJECT_NAME - Build # $BUILD_NUMBER - Approve Change Request',
                to: '${Mail_Addr}'

                    echo "Destruptive changes received."
                    echo "Seeking Release Manager approval for Schema changes in process..."
                    stage('Seek Release Manager approval for Schema changes') {
                        if (!Skip_Stage) {
                            input "Submit job to change schema definitions?"

                            build job: 'Execute Changes',
                                parameters: [string(name: 'Job_ID', value: "${Job_ID}"),
                                    string(name: 'TSO_ID', value: "${params.TSO_ID}"),
                                    password(name: 'TSO_PSWD', value: "${params.TSO_PSWD}")
                                ]
                        } else {
                            echo "Stage Get Approval Skipped"
                        }
                    }
                }
        }
		else {
            echo "Stage Skipped"
        }
    }

    stage('Run Regression') {
        echo "We are running our regression suite"
    }

    stage('Deploy updated Application') {
        sleep time: 785, unit: 'MILLISECONDS'
        echo "I deployed"
    }

    stage('Cleanup') {
        sleep time: 385, unit: 'MILLISECONDS'
        echo "I deployed"
    }
}