{
  description = "Storitad - Legacy Journal Capture App";

  inputs = {
    nixpkgs.url = "github:NixOS/nixpkgs/nixos-unstable";
    flake-utils.url = "github:numtide/flake-utils";
    android-nixpkgs = {
      url = "github:tadfisher/android-nixpkgs";
      inputs.nixpkgs.follows = "nixpkgs";
    };
  };

  outputs = { self, nixpkgs, flake-utils, android-nixpkgs }:
    flake-utils.lib.eachDefaultSystem (system:
      let
        pkgs = nixpkgs.legacyPackages.${system};
        androidSdk = android-nixpkgs.sdk.${system} (sdkPkgs: with sdkPkgs; [
          build-tools-34-0-0
          build-tools-35-0-0
          cmdline-tools-latest
          platform-tools
          platforms-android-34
          platforms-android-35
          ndk-26-3-11579264
          cmake-3-22-1
          emulator
        ]);
        pythonEnv = pkgs.python311.withPackages (ps: with ps; [
          click jinja2 pyyaml pytest
        ]);

        storitadIngest = pkgs.python311Packages.buildPythonApplication {
          pname = "storitad-ingest";
          version = "0.1.0";
          pyproject = true;
          src = ./ingest;
          build-system = [ pkgs.python311Packages.setuptools ];
          dependencies = with pkgs.python311Packages; [
            click jinja2 pyyaml
          ];
          # Put whisper-cli + ffmpeg on PATH at runtime so subprocess.check_call
          # finds them without user-visible $PATH munging.
          nativeBuildInputs = [ pkgs.makeWrapper ];
          postFixup = ''
            wrapProgram $out/bin/storitad-pull \
              --unset PYTHONPATH \
              --prefix PATH : ${pkgs.lib.makeBinPath [
                pkgs.whisper-cpp
                pkgs.ffmpeg-headless
                pkgs.android-tools
              ]}
          '';
          doCheck = false;
        };
      in
      {
        packages.default = storitadIngest;
        packages.storitad-ingest = storitadIngest;
        apps.default = {
          type = "app";
          program = "${storitadIngest}/bin/storitad-pull";
        };
        apps.storitad-pull = self.apps.${system}.default;

        devShells.default = pkgs.mkShell {
          buildInputs = [
            storitadIngest
            androidSdk
            pkgs.jdk17
            pkgs.gradle
            pkgs.kotlin
            pythonEnv
            pkgs.ffmpeg-headless
            pkgs.whisper-cpp
            pkgs.android-tools
          ];
          ANDROID_HOME = "${androidSdk}/share/android-sdk";
          ANDROID_SDK_ROOT = "${androidSdk}/share/android-sdk";
          ANDROID_NDK_ROOT = "${androidSdk}/share/android-sdk/ndk/26.3.11579264";
          JAVA_HOME = "${pkgs.jdk17}";
          GRADLE_OPTS = "-Dorg.gradle.project.android.aapt2FromMavenOverride=${androidSdk}/share/android-sdk/build-tools/35.0.0/aapt2";
        };
      });
}
