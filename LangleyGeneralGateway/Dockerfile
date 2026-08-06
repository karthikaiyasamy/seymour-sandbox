# Dockerfile for Langley General Gateway (C# .NET 10 - Port 8083)
FROM mcr.microsoft.com/dotnet/sdk:10.0-preview AS build
WORKDIR /src
COPY ["LangleyGeneralGateway.csproj", "./"]
RUN dotnet restore "LangleyGeneralGateway.csproj"
COPY . .
RUN dotnet publish "LangleyGeneralGateway.csproj" -c Release -o /app/publish

FROM mcr.microsoft.com/dotnet/aspnet:10.0-preview AS final
WORKDIR /app
COPY --from=build /app/publish .
EXPOSE 8083
ENV ASPNETCORE_URLS=http://+:8083
ENTRYPOINT ["dotnet", "LangleyGeneralGateway.dll"]
