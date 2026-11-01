# Copyright (c) 2026 AoSankaku. OpenMapMC Delta modifications; derived from Odyssey Map by NAIZO under Apache-2.0.
# Checks the mapped native method descriptors required by the shared deathpoint mixins without starting Minecraft.
param(
    [Parameter(Mandatory = $true)][string]$MinecraftJar,
    [string]$JdkHome = $env:JAVA_HOME
)
$ErrorActionPreference = 'Stop'
if (!(Test-Path -LiteralPath $MinecraftJar -PathType Leaf)) { throw "Mapped Minecraft jar not found: $MinecraftJar" }
if (!$JdkHome) { throw 'Set JAVA_HOME or pass -JdkHome (Java 25 for Minecraft 26.2).' }
$javap = Join-Path $JdkHome 'bin/javap.exe'
if (!(Test-Path -LiteralPath $javap -PathType Leaf)) { throw "javap was not found: $javap" }

function Get-ClassDump([string]$ClassName) {
    $dump = & $javap -classpath $MinecraftJar -p -s -c $ClassName 2>&1
    if ($LASTEXITCODE -ne 0) { throw "Could not inspect native target $ClassName`: $($dump -join "`n")" }
    return $dump -join "`n"
}

function Assert-MethodDescriptor([string]$ClassName, [string]$Signature, [string]$Descriptor) {
    $dump = Get-ClassDump $ClassName
    $pattern = '(?m)^\s*[^\r\n]*' + [regex]::Escape($Signature) + ';\r?\n\s*descriptor: ' + [regex]::Escape($Descriptor) + '\s*$'
    if ($dump -notmatch $pattern) { throw "Native target mismatch: $ClassName.$Signature descriptor $Descriptor is absent." }
    Write-Host "PASS $ClassName.$Signature $Descriptor"
    return $dump
}

$serverPlayer = Assert-MethodDescriptor 'net.minecraft.server.level.ServerPlayer' `
    'die(net.minecraft.world.damagesource.DamageSource)' '(Lnet/minecraft/world/damagesource/DamageSource;)V'
$serverPlayer = Assert-MethodDescriptor 'net.minecraft.server.level.ServerPlayer' `
    'drop(net.minecraft.world.item.ItemStack, boolean, boolean)' `
    '(Lnet/minecraft/world/item/ItemStack;ZZ)Lnet/minecraft/world/entity/item/ItemEntity;'
$player = Get-ClassDump 'net.minecraft.world.entity.player.Player'
$playerDropPattern = '(?m)^\s*[^\r\n]*' + [regex]::Escape('drop(net.minecraft.world.item.ItemStack, boolean, boolean)') + ';\r?\n\s*descriptor: \(Lnet/minecraft/world/item/ItemStack;ZZ\)Lnet/minecraft/world/entity/item/ItemEntity;\s*$'
if ($player -match $playerDropPattern) { throw 'Player declares its own death-drop overload; the LivingEntity return hook may be bypassed.' }
$delegationPattern = '(?m)^\s*\d+: invokespecial .*// Method net/minecraft/world/entity/player/Player\.drop:\(Lnet/minecraft/world/item/ItemStack;ZZ\)Lnet/minecraft/world/entity/item/ItemEntity;'
if ($serverPlayer -notmatch $delegationPattern) { throw 'ServerPlayer.drop no longer delegates to Player.drop; recheck the LivingEntity hook path.' }
[void](Assert-MethodDescriptor 'net.minecraft.world.entity.LivingEntity' `
    'drop(net.minecraft.world.item.ItemStack, boolean, boolean)' `
    '(Lnet/minecraft/world/item/ItemStack;ZZ)Lnet/minecraft/world/entity/item/ItemEntity;')
Write-Output 'PASS ServerPlayer.drop delegates through Player to LivingEntity.drop and returns its ItemEntity'

[void](Assert-MethodDescriptor 'net.minecraft.server.level.ServerLevel' `
    'addFreshEntity(net.minecraft.world.entity.Entity)' '(Lnet/minecraft/world/entity/Entity;)Z')
[void](Assert-MethodDescriptor 'net.minecraft.world.entity.item.ItemEntity' `
    'playerTouch(net.minecraft.world.entity.player.Player)' '(Lnet/minecraft/world/entity/player/Player;)V')
[void](Assert-MethodDescriptor 'net.minecraft.world.entity.item.ItemEntity' `
    'merge(net.minecraft.world.entity.item.ItemEntity, net.minecraft.world.item.ItemStack, net.minecraft.world.entity.item.ItemEntity, net.minecraft.world.item.ItemStack)' `
    '(Lnet/minecraft/world/entity/item/ItemEntity;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/entity/item/ItemEntity;Lnet/minecraft/world/item/ItemStack;)V')
[void](Assert-MethodDescriptor 'net.minecraft.world.entity.Entity' `
    'setRemoved(net.minecraft.world.entity.Entity$RemovalReason)' '(Lnet/minecraft/world/entity/Entity$RemovalReason;)V')
Write-Output 'Deathpoint native hook targets verified.'
