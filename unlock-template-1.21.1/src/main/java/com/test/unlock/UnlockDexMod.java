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

    // 获取玩家名称的工具方法
    private static String getPlayerName() {
        try {
            // 尝试获取Minecraft实例
            Class<?> minecraftClass = Class.forName("net.minecraft.client.Minecraft");
            Method getInstanceMethod = minecraftClass.getMethod("getInstance");
            Object minecraft = getInstanceMethod.invoke(null);

            // 尝试获取玩家
            Method getPlayerMethod = minecraftClass.getMethod("getPlayer");
            Object player = getPlayerMethod.invoke(minecraft);

            // 获取玩家名称
            Method getNameMethod = player.getClass().getMethod("getName");
            return (String) getNameMethod.invoke(player);
        } catch (Exception e) {
            LOGGER.warn("获取玩家名称失败: " + e.getMessage());
            return "Player"; // 返回默认名称
        }
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

            // 5. 获取直接操作图鉴的方法
            Method unlockSpeciesMethod = null;
            try {
                // 尝试查找直接解锁宝可梦的方法
                unlockSpeciesMethod = clientPokedexManager.getClass().getMethod("unlockSpecies", Identifier.class);
                LOGGER.info("找到直接解锁方法: unlockSpecies");
            } catch (NoSuchMethodException e) {
                LOGGER.info("未找到直接解锁方法，将使用详细解锁流程");
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

                    // 如果找到了直接解锁方法，使用它
                    if (unlockSpeciesMethod != null) {
                        unlockSpeciesMethod.invoke(clientPokedexManager, speciesId);
                        count++;
                        continue;
                    }

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

            // 7. 尝试使用更直接的方法解锁所有图鉴
            try {
                Method unlockAllMethod = clientPokedexManager.getClass().getMethod("unlockAll");
                if (unlockAllMethod != null) {
                    LOGGER.info("找到unlockAll方法，尝试直接解锁所有图鉴");
                    unlockAllMethod.invoke(clientPokedexManager);
                }
            } catch (NoSuchMethodException e) {
                LOGGER.info("未找到unlockAll方法，继续使用常规解锁流程");
            } catch (Exception e) {
                LOGGER.warn("使用unlockAll方法失败: " + e.getMessage());
            }

            // 8. 尝试使用PokedexManager保存图鉴数据
            try {
                // 尝试获取PokedexManager实例
                Class<?> pokedexManagerClass = Class.forName("com.cobblemon.mod.common.api.pokedex.PokedexManager");
                Field pokedexManagerInstanceField = pokedexManagerClass.getDeclaredField("INSTANCE");
                pokedexManagerInstanceField.setAccessible(true);
                Object pokedexManager = pokedexManagerInstanceField.get(null);

                LOGGER.info("获取到PokedexManager实例: " + pokedexManager);

                if (pokedexManager != null) {
                    // 尝试获取玩家的图鉴数据
                    Method getPlayerDataMethod = pokedexManagerClass.getMethod("getPlayerData", String.class);
                    Object playerData = getPlayerDataMethod.invoke(pokedexManager, getPlayerName());

                    if (playerData != null) {
                        LOGGER.info("获取到玩家图鉴数据");

                        // 尝试将客户端图鉴数据同步到PokedexManager
                        Method syncMethod = pokedexManagerClass.getMethod("syncToManager", clientPokedexManager.getClass());
                        if (syncMethod != null) {
                            syncMethod.invoke(pokedexManager, clientPokedexManager);
                            LOGGER.info("已将客户端图鉴数据同步到PokedexManager");
                        }

                        // 尝试保存图鉴数据
                        Method saveMethod = pokedexManagerClass.getMethod("save");
                        saveMethod.invoke(pokedexManager);
                        LOGGER.info("已通过PokedexManager保存图鉴数据");

                        // 尝试广播图鉴更新
                        Method broadcastUpdateMethod = pokedexManagerClass.getMethod("broadcastUpdate", String.class);
                        broadcastUpdateMethod.invoke(pokedexManager, getPlayerName());
                        LOGGER.info("已广播图鉴更新");
                    }
                }
            } catch (Exception e) {                LOGGER.warn("使用PokedexManager保存图鉴数据失败: " + e.getMessage());
                e.printStackTrace();
            }

            // 9. 清除计算值缓存
            try {
                Method clearCalculatedValuesMethod = clientPokedexManager.getClass().getMethod("clearCalculatedValues");
                clearCalculatedValuesMethod.invoke(clientPokedexManager);
                LOGGER.info("已清除计算值缓存");
            } catch (Exception e) {
                LOGGER.warn("清除计算值缓存失败: " + e.getMessage());
            }

            // 10. 标记为已修改
            try {
                Method markDirtyMethod = clientPokedexManager.getClass().getMethod("markDirty");
                markDirtyMethod.invoke(clientPokedexManager);
                LOGGER.info("已标记图鉴数据为已修改");
            } catch (Exception e) {
                LOGGER.warn("标记图鉴数据为已修改失败: " + e.getMessage());
            }

            // 11. 保存图鉴数据
            try {
                Method saveMethod = clientPokedexManager.getClass().getMethod("save");
                saveMethod.invoke(clientPokedexManager);
                LOGGER.info("图鉴数据已保存");
            } catch (Exception e) {
                LOGGER.warn("保存图鉴数据失败: " + e.getMessage());
            }

            // 12. 尝试发送网络包通知服务器
            try {
                Class<?> networkClass = Class.forName("com.cobblemon.mod.common.api.net.NetworkManager");
                Field instanceField2 = networkClass.getDeclaredField("INSTANCE");
                instanceField2.setAccessible(true);
                Object networkManager = instanceField2.get(null);

                Method sendPacketMethod = networkClass.getMethod("sendToServer", Class.forName("com.cobblemon.mod.common.api.net.NetworkPacket"));

                // 尝试创建同步图鉴的数据包
                Class<?> syncPacketClass = Class.forName("com.cobblemon.mod.common.api.pokedex.SyncPokedexPacket");
                Object syncPacket = syncPacketClass.getDeclaredConstructor().newInstance();

                sendPacketMethod.invoke(networkManager, syncPacket);
                LOGGER.info("已发送图鉴同步数据包到服务器");
            } catch (Exception e) {
                LOGGER.warn("发送图鉴同步数据包失败: " + e.getMessage());
            }

            // 13. 尝试强制刷新客户端UI
            try {
                // 尝试获取刷新UI的方法
                Method refreshUIMethod = clientPokedexManager.getClass().getMethod("refreshUI");
                refreshUIMethod.invoke(clientPokedexManager);
                LOGGER.info("已刷新图鉴UI");
            } catch (NoSuchMethodException e) {
                LOGGER.info("未找到refreshUI方法，尝试其他方式刷新UI");
                try {
                    // 尝试获取其他可能的UI刷新方法
                    Method updateUIMethod = clientPokedexManager.getClass().getMethod("updateUI");
                    updateUIMethod.invoke(clientPokedexManager);
                    LOGGER.info("已通过updateUI方法刷新图鉴UI");
                } catch (Exception ex) {
                    LOGGER.warn("刷新图鉴UI失败: " + ex.getMessage());
                }
            } catch (Exception e) {
                LOGGER.warn("刷新图鉴UI失败: " + e.getMessage());
            }

            // 14. 尝试直接修改完成度统计
            try {
                Method getCompletionStatsMethod = clientPokedexManager.getClass().getMethod("getCompletionStats");
                Object completionStats = getCompletionStatsMethod.invoke(clientPokedexManager);

                if (completionStats != null) {
                    // 尝试设置完成度为100%
                    Class<?> completionStatsClass = completionStats.getClass();
                    Field seenField = completionStatsClass.getDeclaredField("seen");
                    caughtField = completionStatsClass.getDeclaredField("caught");

                    seenField.setAccessible(true);
                    caughtField.setAccessible(true);

                    // 获取总数
                    Field totalField = completionStatsClass.getDeclaredField("total");
                    totalField.setAccessible(true);
                    int total = (int) totalField.get(completionStats);

                    // 设置为全部已见到和已捕获
                    seenField.set(completionStats, total);
                    caughtField.set(completionStats, total);

                    LOGGER.info("已直接修改完成度统计为100%");
                }
            } catch (Exception e) {
                LOGGER.warn("修改完成度统计失败: " + e.getMessage());
            }

            // 15. 尝试触发成就解锁
            try {
                Class<?> achievementClass = Class.forName("com.cobblemon.mod.common.api.achievements.AchievementManager");
                Field instanceField3 = achievementClass.getDeclaredField("INSTANCE");
                instanceField3.setAccessible(true);
                Object achievementManager = instanceField3.get(null);

                Method unlockPokedexAchievementsMethod = achievementClass.getMethod("unlockPokedexAchievements");
                if (unlockPokedexAchievementsMethod != null) {
                    unlockPokedexAchievementsMethod.invoke(achievementManager);
                    LOGGER.info("已触发图鉴相关成就解锁");
                }
            } catch (Exception e) {
                LOGGER.warn("触发图鉴成就解锁失败: " + e.getMessage());
            }

            // 16. 最后再次保存确保数据持久化
            try {
                // 尝试使用更强力的保存方法
                Method forceSaveMethod = clientPokedexManager.getClass().getMethod("forceSave");
                if (forceSaveMethod != null) {
                    forceSaveMethod.invoke(clientPokedexManager);
                    LOGGER.info("已强制保存图鉴数据");
                }
            } catch (NoSuchMethodException e) {
                // 如果没有forceSave方法，再次调用普通save方法
                try {
                    Method saveMethod = clientPokedexManager.getClass().getMethod("save");
                    saveMethod.invoke(clientPokedexManager);
                    LOGGER.info("已再次保存图鉴数据");
                } catch (Exception ex) {
                    LOGGER.warn("再次保存图鉴数据失败: " + ex.getMessage());
                }
            } catch (Exception e) {
                LOGGER.warn("强制保存图鉴数据失败: " + e.getMessage());
            }

            LOGGER.info("客户端图鉴已全部解锁，共解锁 " + count + " 个宝可梦");

        } catch (Exception e) {
            LOGGER.error("解锁图鉴时发生错误: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
