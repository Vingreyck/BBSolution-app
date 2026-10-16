// Lê o envio da foto capturado pelo AnexoMultipartTest com o mesmo leitor de
// formulário do servidor (req.formData() do Node). Rode depois do teste:
//
//   ./gradlew :app:testDebugUnitTest --tests "*AnexoMultipartTest*"
//   node app/src/test/conferir-anexo.mjs
//
// Sai com erro se o servidor não conseguiria ler a foto — a falha que fazia
// o app mostrar "Esperava um formulário multipart com o arquivo".
import fs from "node:fs";

const pasta = new URL("../../build/captura-anexo/", import.meta.url);
const cabecalhos = fs.readFileSync(new URL("cabecalhos.txt", pasta), "utf8");
const tipo = cabecalhos.split("\n").find((l) => l.toLowerCase().startsWith("content-type:")).split(": ").slice(1).join(": ").trim();
const corpo = fs.readFileSync(new URL("corpo.bin", pasta));

const form = await new Response(corpo, { headers: { "content-type": tipo } }).formData();
const arquivo = form.get("arquivo");
if (!(arquivo instanceof File) || arquivo.size === 0) {
  console.error("FALHOU: o campo arquivo não chegou como arquivo.");
  process.exit(1);
}
console.log("OK:", [...form.keys()].join(", "), `| arquivo = ${arquivo.name}, ${arquivo.size} bytes`);
