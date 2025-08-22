package studio.phaseshift.metatron;

import com.github.tjake.jlama.model.AbstractModel;
import com.github.tjake.jlama.model.ModelSupport;
import com.github.tjake.jlama.model.functions.Generator;
import com.github.tjake.jlama.safetensors.DType;
import com.github.tjake.jlama.safetensors.prompt.PromptContext;
import com.github.tjake.jlama.util.Downloader;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.UUID;

public class SimpleInferenceTest {

    @Test
    void testInference() throws Exception {
        String model = "TinyLlama/TinyLlama-1.1B-Chat-v1.0";
        String workingDirectory = "/srv/huggingface";

        String prompt = "What is the best season to plant avocados?";

        // Downloads the model or just returns the local path if it's already downloaded
        File localModelPath = new Downloader(workingDirectory, model).huggingFaceModel();

        // Loads the quantized model and specified use of quantized memory
        AbstractModel m = ModelSupport.loadModel(localModelPath, DType.F32, DType.BF16);
        PromptContext ctx = m.promptSupport().get().builder().addSystemMessage("You are a helpful chatbot who writes short responses.").addUserMessage(prompt).build();
        System.out.println("Prompt: " + ctx.getPrompt() + "\n");
        // Generates a response to the prompt and prints it
        // The api allows for streaming or non-streaming responses
        // The response is generated with a temperature of 0.7 and a max token length of 256
        Generator.Response r = m.generateBuilder()
                .session(UUID.randomUUID()) //By default, UUID.randomUUID()
                .promptContext(ctx) // Required or use prompt(String text)
                .ntokens(256) //By default, 256
                .temperature(0.0f) //By default, 0.0f
                .onTokenWithTimings((s, aFloat) -> {
                }) //By default, (s, aFloat) -> {}, nothing
                .generate();

        System.out.println(r.responseText);
    }
}
