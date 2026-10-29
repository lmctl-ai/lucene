/* SPDX-License-Identifier: Apache-2.0 */
package io.lmctl.lucene;

import io.lmctl.lmcode.*;
import java.nio.file.*;
import java.util.*;

public final class Main {
  private static final String USAGE =
      "lmlucene --version | --schema | --dsl-schema [--json] | --dsl-signatures | --dsl FILE | run"
          + " --program FILE [--dryrun] [--readonly]";

  public static String cliSchema() {
    return Host.json(
        Map.of(
            "schema",
            "cli.schema.v1",
            "tool",
            "lmlucene",
            "version",
            "0.1.0",
            "policy",
            Map.of("dryrun", true, "readonly", true),
            "inputs",
            List.of("flags", "json"),
            "operations",
            List.of(
                Map.of(
                    "name",
                    "run",
                    "access",
                    "write",
                    "description",
                    "Execute trusted lmcode bytecode against Lucene; may modify indexes and files."
                        + " Dry-run verifies loading without executing the body or rules.",
                    "params",
                    List.of(
                        Map.of(
                            "name",
                            "program",
                            "type",
                            "string",
                            "description",
                            "Path to a DSLB v6 program"))))));
  }

  public static void main(String[] args) {
    System.exit(run(args));
  }

  public static int run(String[] args) {
    String phase = "load";
    try (Host host = new Host()) {
      if (Arrays.equals(args, new String[] {"--version"})) {
        System.out.println("lmlucene 0.1.0 (Lucene 10.5.2; DSLB v6)");
        return 0;
      }
      if (Arrays.equals(args, new String[] {"--help"})) {
        System.out.println(USAGE);
        return 0;
      }
      if (Arrays.equals(args, new String[] {"--schema"})
          || Arrays.equals(args, new String[] {"--cli-schema"})
          || Arrays.equals(args, new String[] {"--cli-schema", "--json"})) {
        System.out.println(cliSchema());
        return 0;
      }
      if (Arrays.equals(args, new String[] {"--dsl-schema"})
          || Arrays.equals(args, new String[] {"--dsl-schema", "--json"})) {
        System.out.write(host.schema());
        return 0;
      }
      if (Arrays.equals(args, new String[] {"--dsl-signatures"})) {
        host.registry.signatures().forEach(System.out::println);
        return 0;
      }
      boolean dry = false, readonly = false;
      String file = null;
      if (args.length == 2 && args[0].equals("--dsl")) file = args[1];
      else if (args.length > 0 && args[0].equals("run")) {
        for (int i = 1; i < args.length; i++) {
          if (args[i].equals("--program") && file == null && i + 1 < args.length) file = args[++i];
          else if ((args[i].equals("--input-json") || args[i].equals("--input-file"))
              && file == null
              && i + 1 < args.length) {
            boolean fromFile = args[i].equals("--input-file");
            String input = args[++i];
            try {
              Map<String, Object> document =
                  Json.object(Json.parse(fromFile ? Files.readString(Path.of(input)) : input));
              if (!document.keySet().equals(Set.of("op", "params"))
                  || !"run".equals(document.get("op")))
                throw new IllegalArgumentException("expected op run and params");
              Map<String, Object> params = Json.object(document.get("params"));
              if (!params.keySet().equals(Set.of("program"))
                  || !(params.get("program") instanceof String))
                throw new IllegalArgumentException("expected one string parameter: program");
              file = (String) params.get("program");
            } catch (Exception e) {
              System.out.println(
                  Reports.errorJson(
                      "cli",
                      new VmException("CLI_USAGE", "invalid JSON input: " + e.getMessage())));
              return 2;
            }
          } else if (args[i].equals("--dryrun") && !dry) dry = true;
          else if (args[i].equals("--readonly") && !readonly) readonly = true;
          else {
            file = null;
            break;
          }
        }
      }
      if (file == null) {
        System.out.println(Reports.errorJson("cli", new VmException("CLI_USAGE", USAGE)));
        return 2;
      }
      if (readonly) {
        System.out.println(
            Reports.errorJson(
                "policy",
                new VmException(
                    "READONLY",
                    "run executes arbitrary trusted bytecode and is classified as write")));
        return 3;
      }
      byte[] bytes = Files.readAllBytes(Path.of(file));
      host.registry.checkSchemaSignatures(host.schema());
      Program program = Program.load(bytes, host.schema(), host.registry);
      host.attach(bytes);
      if (dry) return Reports.ok("{\"dryrun\":true,\"validated\":true}");
      phase = "run";
      program.run(new Context(), host.registry);
      // Close before reporting success, so cleanup errors cannot produce a second result line.
      host.close();
      return Reports.ok(host.result());
    } catch (VmException e) {
      return Reports.error(phase, e);
    } catch (Exception e) {
      return Reports.error(
          phase,
          new VmException(
              phase.equals("load") ? "LOAD_IO" : "RUN_HOST",
              Objects.toString(e.getMessage(), e.getClass().getSimpleName())));
    }
  }
}
