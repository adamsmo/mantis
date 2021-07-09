{ cfg, pkgs, ... }:

with cfg.steps.commands;
let
  commonAttrs = {
    retry.automatic = true;
    agents.queue = "project42";
  };
in
{
  steps.commands = {
    nixExpr = commonAttrs // {
      label = "ensure Nix expressions are up-to-date";
      command = ''
        ./update-nix.sh --check
      '';
      retry.automatic = false;
      artifactPaths = [
        "nix-expr.patch"
      ];
    };

    scalafixAndFmt = commonAttrs // {
      label = "scalafix & scalafmt";
      command = ''
        nix-shell --run '$SBT formatCheck'
      '';
      retry.automatic = false;
    };

    compile = commonAttrs // {
      label = "compile everything";
      dependsOn = [ scalafixAndFmt ];
      command = ''
        nix-shell --run '$SBT compile-all'
      '';
      retry.automatic = false;
    };

    style = commonAttrs // {
      dependsOn = [ compile ];
      label = "scalastyle";
      command = ''
        nix-shell --run '$SBT scalastyle test:scalastyle'
      '';
      retry.automatic = false;
    };

    test-bytes = commonAttrs // {
      dependsOn = [ compile ];
      label = "bytes tests";
      command = ''
        nix-shell --run '$SBT coverage bytes/test'
      '';
      artifactPaths = [
        "bytes/target/test-reports/**/*"
        "bytes/target/scala-2.13/scoverage-report/**/*"
        "bytes/target/scala-2.13/coverage-report/**/*"
      ];
    };

    test-crypto = commonAttrs // {
      dependsOn = [ compile ];
      label = "Crypto tests";
      command = ''
        nix-shell --run '$SBT coverage crypto/test'
      '';
      artifactPaths = [
        "crypto/target/test-reports/**/*"
        "crypto/target/scala-2.13/scoverage-report/**/*"
        "crypto/target/scala-2.13/coverage-report/**/*"
      ];
    };

    test-rlp = commonAttrs // {
      dependsOn = [ compile ];
      label = "RLP tests";
      command = ''
        nix-shell --run '$SBT coverage rlp/test'
      '';
      artifactPaths = [
        "rlp/target/test-reports/**/*"
        "rlp/target/scala-2.13/scoverage-report/**/*"
        "rlp/target/scala-2.13/coverage-report/**/*"
      ];
    };

    test-unit = commonAttrs // {
      dependsOn = [ compile ];
      label = "unit tests";
      command = ''
        nix-shell --run '$SBT coverage test || $SBT testQuick'
      '';
      artifactPaths = [
        "target/test-reports/**/*"
        "target/scala-2.13/scoverage-report/**/*"
        "target/scala-2.13/coverage-report/**/*"
      ];
    };

    annotate-test-reports = commonAttrs // {
      dependsOn = [ test-unit ];
      label = "annotate test reports";
      command = "junit-annotate";
      allowDependencyFailure = true;
      plugins = [{
        "junit-annotate#1.9.0" = {
          artifacts = "target/test-reports/*.xml";
          report-slowest = 50;
        };
      }];
    };

    test-evm = commonAttrs // {
      dependsOn = [ compile ];
      label = "EVM tests";
      command = ''
        nix-shell --run '$SBT coverage evm:test'
      '';
      artifactPaths = [
        "target/test-reports/**/*"
        "target/scala-2.13/scoverage-report/**/*"
        "target/scala-2.13/coverage-report/**/*"
      ];
    };

    test-ets = commonAttrs // {
      dependsOn = [ compile ];
      label = "ETS";
      command = ''
        nix-shell --run './test-ets.sh'
      '';
      softFail = true;
      retry.automatic = false;
      artifactPaths = [
        "mantis-log.txt"
        "retesteth-GeneralStateTests-log.txt"
        "retesteth-BlockchainTests-log.txt"
      ];
    };

    test-integration = commonAttrs // {
      dependsOn = [ compile ];
      label = "integration tests";
      command = ''
        nix-shell --run '$SBT it:test || $SBT it:testQuick'
      '';
      artifactPaths = [ "target/test-reports/**/*" ];
      timeoutInMinutes = 60;
    };

    coverageReport = commonAttrs // {
      dependsOn = [ test-unit test-evm ];
      label = "coverage report";
      command = ''
        nix-shell --run '$SBT coverageReport coverageAggregate'
      '';
    };

    additional = commonAttrs // {
      dependsOn = [ compile test-integration ];
      label = "additional compilation & dist";
      command = ''
        nix-shell --run '$SBT benchmark:compile dist'
      '';
      artifactPaths = [
        "target/universal/mantis-*.zip"
      ];
    };

    publish = commonAttrs // {
      dependsOn = [ test-crypto test-rlp test-unit ];
      label = "Publishing libraries to Maven";
      command = ''
        nix-env -iA nixpkgs.gnupg && nix-shell --run '.buildkite/publish.sh'
      '';
      branches = "master develop";
      timeoutInMinutes = 30;
    };
  };
}
