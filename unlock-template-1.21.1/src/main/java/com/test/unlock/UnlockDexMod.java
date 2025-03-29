package com.test.unlock;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.minecraft.text.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Map;
import net.minecraft.util.Identifier;

public class UnlockDexMod implements ClientModInitializer {
    public static final String MOD_ID = "unlock";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitializeClient() {
        LOGGER.info("初始化客户端解锁图鉴Mod");

        // 注册客户端命令
        registerClientCommands();
        ClientEventHandler.init();
    }

    private void registerClientCommands() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(ClientCommandManager.literal("unlockdexclient")
                    .executes(context -> {
                        unlockAllPokemonClientSide();
                        context.getSource().sendFeedback(Text.literal("§a已尝试在客户端解锁所有宝可梦图鉴!"));
                        return 1;
                    })
            );
        });
    }

    public static void unlockAllPokemonClientSide() {
        try {
            LOGGER.info("开始尝试解锁图鉴...");

            // 1. 获取CobblemonClient实例
            Class<?> cobblemonClientClass = Class.forName("com.cobblemon.mod.common.client.CobblemonClient");
            Field instanceField = cobblemonClientClass.getDeclaredField("INSTANCE");
            instanceField.setAccessible(true);
            Object clientInstance = instanceField.get(null);
            LOGGER.info("获取到CobblemonClient实例: " + clientInstance);

            // 2. 获取客户端图鉴数据
            Method getClientPokedexDataMethod = cobblemonClientClass.getMethod("getClientPokedexData");
            Object clientPokedexManager = getClientPokedexDataMethod.invoke(clientInstance);
            LOGGER.info("获取到客户端图鉴数据: " + clientPokedexManager);

            if (clientPokedexManager == null) {
                LOGGER.error("无法获取图鉴数据，解锁失败");
                return;
            }

            // 3. 获取所有宝可梦种类
            Class<?> pokemonSpeciesClass = Class.forName("com.cobblemon.mod.common.pokemon.Species");
            Class<?> pokemonSpeciesRegistryClass = Class.forName("com.cobblemon.mod.common.api.pokemon.PokemonSpecies");

            Field speciesRegistryInstanceField = pokemonSpeciesRegistryClass.getDeclaredField("INSTANCE");
            speciesRegistryInstanceField.setAccessible(true);
            Object speciesRegistry = speciesRegistryInstanceField.get(null);

            Method getSpeciesMethod = pokemonSpeciesRegistryClass.getMethod("getSpecies");
            Collection<?> speciesList = (Collection<?>) getSpeciesMethod.invoke(speciesRegistry);

            // 4. 获取PokedexEntryProgress枚举
            Class<?> pokedexEntryProgressClass = Class.forName("com.cobblemon.mod.common.api.pokedex.PokedexEntryProgress");
            Field caughtField = pokedexEntryProgressClass.getDeclaredField("CAUGHT");
            caughtField.setAccessible(true);
            Object caughtValue = caughtField.get(null);

            // 5. 打印ClientPokedexManager的所有方法，帮助调试
            LOGGER.info("ClientPokedexManager方法:");
            for (Method method : clientPokedexManager.getClass().getMethods()) {
                LOGGER.info("方法: " + method.getName() + " 返回类型: " + method.getReturnType().getName());
            }

            // 6. 解锁所有宝可梦
            int count = 0;
            for (Object species : speciesList) {
                try {
                    // 获取宝可梦ID
                    Method getResourceIdentifierMethod = pokemonSpeciesClass.getMethod("getResourceIdentifier");
                    Object speciesId = getResourceIdentifierMethod.invoke(species);

                    // 打印宝可梦信息
                    Method getNameMethod = pokemonSpeciesClass.getMethod("getName");
                    String speciesName = (String) getNameMethod.invoke(species);
                    LOGGER.info("处理宝可梦: " + speciesName + " (ID: " + speciesId + ")");

                    // 获取或创建宝可梦记录
                    Method getOrCreateSpeciesRecordMethod = clientPokedexManager.getClass().getMethod("getOrCreateSpeciesRecord", speciesId.getClass());
                    Object speciesRecord = getOrCreateSpeciesRecordMethod.invoke(clientPokedexManager, speciesId);

                    // 获取所有形态
                    Method getFormsMethod = pokemonSpeciesClass.getMethod("getForms");
                    Collection<?> forms = (Collection<?>) getFormsMethod.invoke(species);

                    // 为每个形态创建记录
                    boolean hasProcessedAnyForm = false;
                    for (Object form : forms) {
                        // 获取形态名称
                        Class<?> formDataClass = form.getClass();
                        Method getFormNameMethod = formDataClass.getMethod("getName");
                        String formName = (String) getFormNameMethod.invoke(form);

                        LOGGER.info("处理形态: " + formName);

                        // 创建形态记录
                        Method getOrCreateFormRecordMethod = speciesRecord.getClass().getMethod("getOrCreateFormRecord", String.class);
                        Object formRecord = getOrCreateFormRecordMethod.invoke(speciesRecord, formName);

                        // 设置为已捕获
                        Method setKnowledgeProgressMethod = formRecord.getClass().getMethod("setKnowledgeProgress", pokedexEntryProgressClass);
                        setKnowledgeProgressMethod.invoke(formRecord, caughtValue);

                        // 添加所有闪光状态和性别
                        Method addAllShinyStatesAndGendersMethod = formRecord.getClass().getMethod("addAllShinyStatesAndGenders");
                        addAllShinyStatesAndGendersMethod.invoke(formRecord);

                        // 尝试添加骑乘数据
                        try {
                            Method addAllRideablesMethod = formRecord.getClass().getMethod("addAllRideables");
                            addAllRideablesMethod.invoke(formRecord);
                        } catch (NoSuchMethodException e) {
                            LOGGER.info("形态 " + formName + " 没有addAllRideables方法，尝试手动添加骑乘数据");
                            try {
                                Method getRideablesMethod = formRecord.getClass().getMethod("getRideables");
                                Collection<Object> rideables = (Collection<Object>) getRideablesMethod.invoke(formRecord);

                                if (rideables.isEmpty()) {
                                    Class<?> rideableClass = Class.forName("com.cobblemon.mod.common.api.pokedex.PokedexRideable");
                                    Object defaultRideable = rideableClass.getDeclaredConstructor().newInstance();
                                    rideables.add(defaultRideable);
                                    LOGGER.info("已为形态 " + formName + " 添加默认骑乘数据");
                                }
                            } catch (Exception ex) {
                                LOGGER.warn("为形态 " + formName + " 添加骑乘数据失败: " + ex.getMessage());
                            }
                        }

                        hasProcessedAnyForm = true;
                    }

                    // 如果没有处理任何形态，处理标准形态
                    if (!hasProcessedAnyForm) {
                        LOGGER.info("处理标准形态");

                        // 获取标准形态
                        Method getStandardFormMethod = pokemonSpeciesClass.getMethod("getStandardForm");
                        Object standardForm = getStandardFormMethod.invoke(species);

                        // 获取标准形态名称
                        Class<?> formDataClass = standardForm.getClass();
                        Method getFormNameMethod = formDataClass.getMethod("getName");
                        String formName = (String) getFormNameMethod.invoke(standardForm);

                        // 创建形态记录
                        Method getOrCreateFormRecordMethod = speciesRecord.getClass().getMethod("getOrCreateFormRecord", String.class);
                        Object formRecord = getOrCreateFormRecordMethod.invoke(speciesRecord, formName);

                        // 设置为已捕获
                        Method setKnowledgeProgressMethod = formRecord.getClass().getMethod("setKnowledgeProgress", pokedexEntryProgressClass);
                        setKnowledgeProgressMethod.invoke(formRecord, caughtValue);

                        // 添加所有闪光状态和性别
                        Method addAllShinyStatesAndGendersMethod = formRecord.getClass().getMethod("addAllShinyStatesAndGenders");
                        addAllShinyStatesAndGendersMethod.invoke(formRecord);

                        // 尝试添加骑乘数据
                        try {
                            Method addAllRideablesMethod = formRecord.getClass().getMethod("addAllRideables");
                            addAllRideablesMethod.invoke(formRecord);
                        } catch (NoSuchMethodException e) {
                            LOGGER.info("标准形态没有addAllRideables方法，尝试手动添加骑乘数据");
                            try {
                                Method getRideablesMethod = formRecord.getClass().getMethod("getRideables");
                                Collection<Object> rideables = (Collection<Object>) getRideablesMethod.invoke(formRecord);

                                if (rideables.isEmpty()) {
                                    Class<?> rideableClass = Class.forName("com.cobblemon.mod.common.api.pokedex.PokedexRideable");
                                    Object defaultRideable = rideableClass.getDeclaredConstructor().newInstance();
                                    rideables.add(defaultRideable);
                                    LOGGER.info("已为标准形态添加默认骑乘数据");
                                }
                            } catch (Exception ex) {
                                LOGGER.warn("为标准形态添加骑乘数据失败: " + ex.getMessage());
                            }
                        }
                    }

                    count++;
                } catch (Exception e) {
                    LOGGER.error("解锁宝可梦失败: " + e.getMessage());
                    e.printStackTrace();
                }
            }

            // 7. 清除计算值缓存
            try {
                Method clearCalculatedValuesMethod = clientPokedexManager.getClass().getMethod("clearCalculatedValues");
                clearCalculatedValuesMethod.invoke(clientPokedexManager);
                LOGGER.info("已清除计算值缓存");
            } catch (Exception e) {
                LOGGER.warn("清除计算值缓存失败: " + e.getMessage());
            }

            // 8. 标记为已修改
            try {
                Method markDirtyMethod = clientPokedexManager.getClass().getMethod("markDirty");
                markDirtyMethod.invoke(clientPokedexManager);
                LOGGER.info("已标记图鉴数据为已修改");
            } catch (Exception e) {
                LOGGER.warn("标记图鉴数据为已修改失败: " + e.getMessage());
            }

            // 9. 保存图鉴数据
            try {
                Method saveMethod = clientPokedexManager.getClass().getMethod("save");
                saveMethod.invoke(clientPokedexManager);
                LOGGER.info("图鉴数据已保存");
            } catch (Exception e) {
                LOGGER.warn("保存图鉴数据失败: " + e.getMessage());
            }

            LOGGER.info("客户端图鉴已全部解锁，共解锁 " + count + " 个宝可梦");
        } catch (Exception e) {
            LOGGER.error("解锁图鉴时发生错误: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
