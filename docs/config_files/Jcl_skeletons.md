# <a id="JCL skeletons> JCL Skeletons
JCL skeletons in ISPF allow building mainframe jobs based on user input without the user having to code the complete JCL. They provide templates containing variables. At 'runtime' these variables get substituted by the input provided by the user. This principle is mimicked here. 

The skeleton JCL in our examples uses strings in brackets '<>' to identify placeholders which get substituted at runtime. Currently, these are fixed names and only these 'variables' can be used to subsitute placeholders by concrete values.

In total there are three pieces of JCL that get generated during runtime.

## <a id="JobCard.jcl"> A job card `JobCard.jcl`
The file `JobCard.jcl` contains a job card that will be used for jobs that get submitted on the mainframe from the pipeline. This way job that get executed by pipeline automation can be distinguished (and executed under different rights) than the normal "user related" job JCL that gets stored with the Topaz for Total test projects.
The current version of the `JobCard.jcl` does not provide any pipeline specific variable subsitution. Any valid JCL specific variable (e.g. `&SYSUID`) may still be used. 

## <a id="deleteDs.skel"> Delete temporary Dataset `deleteDs.skel`
The purpose of this JCL is to submit a job that deletes a dataset. In the context of the pipelines this dataset is supposed to be temporary in use (and contains copybook members that need to be downloaded). The skeleton looks like this
```
//CLEAN   EXEC PGM=IEFBR14
//DELETE DD DISP=(SHR,DELETE,DELETE),DSN=<clean_dsn>
```

The placeholder `<clean_dsn>` will be replaced by a concrete dataset name during runtime.