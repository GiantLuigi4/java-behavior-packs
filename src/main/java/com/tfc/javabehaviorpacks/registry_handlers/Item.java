package com.tfc.javabehaviorpacks.registry_handlers;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.tfc.javabehaviorpacks.CreativeTabCache;
import com.tfc.javabehaviorpacks.EnumFoodQuality;
import com.tfc.javabehaviorpacks.JavaBehaviorPacks;
import com.tfc.javabehaviorpacks.utils.assets_helpers.BedrockMapper;
import net.minecraft.block.BlockState;
import net.minecraft.item.FoodComponent;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.tag.BlockTags;
import net.minecraft.util.Identifier;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.registry.Registry;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Objects;
import java.util.Scanner;
import java.util.concurrent.atomic.AtomicReference;

//https://bedrock.dev/docs/stable/Item
public class Item {
	public static void register(File item) {
		String id = null;
		try {
			StringBuilder s = new StringBuilder();
			Scanner sc = new Scanner(item);
			
			while (sc.hasNextLine()) {
				String line = sc.nextLine();
				//Curse you too bridge., lol
				if (!line.startsWith("//")) {
					s.append(line.trim());
				}
			}
			
			sc.close();

//							JsonObject<JsonObject<?>> itemJSON1 = JsonReader.read(s);
//
//							System.out.println(itemJSON1);
//							System.out.println(itemJSON1.get("\"format_version\""));
//							System.out.println(itemJSON1.get("\"format_version\"").getHeld());
//							System.out.println(itemJSON1
//									.get("\"minecraft:item\"")
//									.get("\"description\"")
//									.get("\"identifier\"").getHeld()
//							);
			
			Gson gson = new Gson();
			
			JsonObject object = gson.fromJson(s.toString(), JsonObject.class);
			
			int maxStack = 64;
			int useTime = 0;
			boolean enchantmentGlint = false;
			
			FoodComponent.Builder food = null;
			
			JsonObject itemObj = object.getAsJsonObject("minecraft:item");
			
			HashMap<String,Float> blockBreakSpeedAmplifiers = new HashMap<>();
			
			if (itemObj.has("components")) {
				JsonObject components = itemObj.getAsJsonObject("components");
				if (components.has("minecraft:max_stack_size")) {
					maxStack = components.getAsJsonPrimitive("minecraft:max_stack_size").getAsInt();
				}
				if (components.has("minecraft:use_duration")) {
					useTime = components.getAsJsonPrimitive("minecraft:use_duration").getAsInt();
				}
				if (components.has("minecraft:foil")) {
					enchantmentGlint = components.getAsJsonPrimitive("minecraft:foil").getAsBoolean();
				}
				if (components.has("minecraft:food")) {
					JsonObject foodDescription = components.getAsJsonObject("minecraft:food");
					food = new FoodComponent.Builder();
					if (foodDescription.has("nutrition")) {
						food = food.hunger(foodDescription.getAsJsonPrimitive("nutrition").getAsInt());
					}
					if (foodDescription.has("saturation_modifier")) {
						try {
							food = food.saturationModifier(Objects.requireNonNull(EnumFoodQuality.forName(
									foodDescription.getAsJsonPrimitive("saturation_modifier").getAsString()
							)).getVal());
						} catch (Throwable ignored) {
						}
					}
					if (foodDescription.has("can_always_eat")) {
						if (foodDescription.getAsJsonPrimitive("can_always_eat").getAsBoolean()) {
							food = food.alwaysEdible();
						}
					}
				}
				
				if (components.has("minecraft:digger")) {
					JsonArray digger = components.getAsJsonObject("minecraft:digger").getAsJsonArray("destroy_speeds");
					if (digger != null) {
						digger.iterator().forEachRemaining((speedElement)->{
							JsonObject element = speedElement.getAsJsonObject();
							String nameOrQuery;
							try {
								nameOrQuery = element.getAsJsonPrimitive("block").getAsString();
							} catch (Throwable ignored) {
								nameOrQuery = element.getAsJsonObject("block").getAsJsonPrimitive("tags").getAsString();
							}
							float speed = element.getAsJsonPrimitive("speed").getAsFloat();
							blockBreakSpeedAmplifiers.put(nameOrQuery,speed);
						});
					}
				}
			}
			
			id = itemObj.getAsJsonObject("description").getAsJsonPrimitive("identifier").getAsString();
			
//			System.out.println(id);
			
			net.minecraft.item.Item.Settings settings = new net.minecraft.item.Item.Settings().maxCount(maxStack);
			
			if (food != null) {
				settings = settings.food(food.build());
			}

//			id = id.toLowerCase();
			
			if (!JavaBehaviorPacks.namespaces.contains(new Identifier(id).getNamespace()))
				JavaBehaviorPacks.namespaces.add(new Identifier(id).getNamespace());
			
			final int finalUseTime = useTime;
			boolean finalEnchantmentGlint = enchantmentGlint;
			net.minecraft.item.Item item1 = new net.minecraft.item.Item(settings.group(CreativeTabCache.bedrockItems)) {
				@Override
				public int getMaxUseTime(ItemStack stack) {
					return finalUseTime;
				}
				
				@Override
				public boolean hasGlint(ItemStack stack) {
					return finalEnchantmentGlint || super.hasGlint(stack);
				}
				
				@Override
				protected boolean isIn(ItemGroup group) {
//					if (CreativeTabCache.getGroupsFor(new ItemStack(this)).contains(group)) return true;
					ArrayList<ItemGroup> groups = CreativeTabCache.getGroupsFor(new ItemStack(this));
					if (groups.contains(group)) return true;
					for (ItemGroup group1 : groups) if (group.getName().equals(group1.getName())) return true;
					return super.isIn(group);
				}
				
				@Override
				public void appendStacks(ItemGroup group, DefaultedList<ItemStack> stacks) {
					super.appendStacks(group, stacks);
//					if (CreativeTabCache.getGroupsFor(new ItemStack(this)).contains(group)) {
//						stacks.add(new ItemStack(this));
//					}
				}
				
				@Override
				public float getMiningSpeedMultiplier(ItemStack stack, BlockState state) {
					Identifier id = Registry.BLOCK.getKey(state.getBlock()).get().getValue();
					if (blockBreakSpeedAmplifiers.containsKey(id.toString()))
						return blockBreakSpeedAmplifiers.get(id.toString());
					
					AtomicReference<Float> speed = new AtomicReference<>(super.getMiningSpeedMultiplier(stack,state));
					
					blockBreakSpeedAmplifiers.forEach((name, val) -> {
						try {
							if (!name.startsWith("q.any_tag(")) return;
							String allTags = name.substring("q.any_tag(".length(), name.length() - 1);
							for (String s : allTags.split(",")) {
								s = s.trim().substring(1, s.trim().length() - 1);
								ArrayList<String> alLTags = BedrockMapper.getAllTags(s);
								for (String tagName : alLTags) {
									if (state.getBlock().isIn(BlockTags.getTagGroup().getTagOrEmpty(new Identifier(tagName)))) {
										speed.set(val);
										return;
									}
								}
							}
						} catch (Throwable ignored) {
						}
					});
					
					return speed.get();
				}
			};
//			if (Registry.ITEM.containsId(new Identifier(id))) {
//				try {
			Registry.register(Registry.ITEM, id, item1);
//				} catch (Throwable ignored) {
//					Registry.ITEM.set(
//							Registry.ITEM.getRawId(
//									Registry.ITEM.get(
//											new Identifier(id)
//									)
//							),
//							RegistryKey.of(
//									Registry.ITEM.getKey(),
//									new Identifier(id)
//							),
//							item1,
//							com.mojang.serialization.Lifecycle.stable()
//					);
//				}
//			} else {
//				Registry.ITEM.set(
//						Registry.ITEM.getRawId(
//								Registry.ITEM.get(
//										new Identifier(id)
//								)
//						),
//						RegistryKey.of(
//								Registry.ITEM.getKey(),
//								new Identifier(id)
//						),
//						item1,
//						com.mojang.serialization.Lifecycle.stable()
//				);
//			}
		} catch (Throwable ignored) {
			System.out.println(id);
			ignored.printStackTrace();
		}
	}
}
